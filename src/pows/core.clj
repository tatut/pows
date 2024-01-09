(ns pows.core
  "Playwright over WebSocket."
  (:require [org.httpkit.server :as http]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire]
            [clojure.string :as str])
  (:import (com.microsoft.playwright Playwright BrowserType$LaunchOptions)
           (com.microsoft.playwright.assertions PlaywrightAssertions))
  (:gen-class))

(def ch->state (atom {}))

(defonce server nil)
(defonce pw (Playwright/create))

(defn close [ch]
  (let [{:keys [context]} (get @ch->state ch)]
    (when context (.close context))))

(defn ensure-state [ch config]
  (or  (get @ch->state ch)
       (let [chromium (.chromium pw)
             options (doto (BrowserType$LaunchOptions.)
                       (.setHeadless (:headless config true)))
             browser (.launch chromium options)
             context (.newContext browser)]
         (.setDefaultTimeout context (:timeout options 10000))
         (let [state {:context context
                      :browser browser
                      :page (.newPage context)}]
           (swap! ch->state assoc ch state)
           state))))

(defn- locate [page locator]
  (loop [at page
         [l & ls] (if (string? locator)
                         [locator]
                         (remove nil? locator))]
    (if-not l
      at
      (recur (.locator at l) ls))))

(defmulti cmd (fn [command _page _loc] (-> command (dissoc :options :locator :not) ffirst)))

(defn assert-that [loc cmd assert-fn]
  (let [asrt (PlaywrightAssertions/assertThat loc)
        asrt (if (:not cmd)
               (.not asrt)
               asrt)]
    (try
      (assert-fn asrt)
      {}
      (catch org.opentest4j.AssertionFailedError afe
        {:success false
         :error (.getMessage afe)
         :actual (.getValue (.getActual afe))
         :expected (.getValue (.getExpected afe))}))))

(defmethod cmd :go [{go :go} page _] (.navigate page go) nil)

(defmacro defassert [kw arity]
  `(defmethod cmd ~kw [cmd# _# loc#]
     (assert-that loc# cmd# (fn [l#]
                              (let [~'args (get cmd# ~kw)]
                                (println "args: " (pr-str ~'args))
                                (~(symbol (str "." (name kw)))
                                 l# ~@(if (= 1 arity)
                                        (list 'args)
                                        (for [i (range 0 arity)]
                                          `(nth ~'args ~i)))))))))
(defmacro defasserts [& asserts]
  `(do
     ~@(for [[kw arity] asserts]
         `(defassert ~kw ~arity))))

(defasserts
  (:hasText 1)
  (:containsText 1)
  (:hasAttribute 2)
  (:hasClass 1)
  (:hasCount 1)
  (:hasCSS 2)
  (:hasId 1)
  (:hasJSProperty 2)
  (:hasValue 2)
  (:hasValues 2)
  (:isAttached 0)
  (:isChecked 0)
  (:isDisabled 0)
  (:isEditable 0)
  (:isEmpty 0)
  (:isEnabled 0)
  (:isFocused 0)
  (:isHidden 0)
  (:isInViewport 0)
  (:isVisible 0))

(defmacro defaction [kw arity]
  `(defmethod cmd ~kw [cmd# _# loc#]
     (let [~'args (get cmd# ~kw)]
       {:result (~(symbol (str "." (name kw))) loc#
                 ~@(for [i (range 0 arity)]
                     `(nth ~'args ~i)))})))

(defmacro defactions [& actions]
  `(do
     ~@(for [[action arity] actions]
         `(defaction ~action ~arity))))

(defactions
  (:click 0)
  (:type 1)
  (:blur 0)
  (:boundingBox 0)
  (:check 0)
  (:clear 0)
  (:dblclick 0)
  (:dispatchEvent 1)
  (:evaluate 1)
  (:fill 1)
  (:focus 0)
  (:hover 0)
  (:innerHTML 0)
  (:innerText 0)
  (:press 1)
  (:selectOption 1)
  (:selectText 1)
  (:setChecked 1)
  (:tap 0)
  (:uncheck 0)
  (:pressSequentially 1))

(defmethod cmd :click [{_ :click :as cmd} _ loc] (.click loc))


(defn handle-cmd [ch {:keys [locator] :as command}]
  (let [state (ensure-state ch (:options cmd))
        loc (some->> locator (locate (:page state)))]
    (try
      (merge {:success true}
             (cmd command (:page state) loc))
      (catch Throwable t
        (log/error t "Error executing command" command)
        {:success false :error (.getMessage t)}))))

(defn ws-handler [req]
  (http/as-channel
   req
   {:on-open (fn [ch]
               (if-not (http/websocket? ch)
                 (http/close ch)
                 (log/info "Client connected")))
    :on-close (fn [ch status]
                (log/info "Client disconnected, status: " status)
                (close ch))
    :on-receive (fn [ch msg]
                  (try
                    (let [cmd (cheshire/decode msg (comp keyword #(str/replace % "_" "-")))
                          resp (handle-cmd ch cmd)]
                      (http/send! ch (cheshire/encode resp)))

                    (catch Throwable t
                      (log/error t "Closing due to error in message: " msg)
                      (close ch))))}))

(defn -main [& [port-number]]
  (let [port (or (some-> port-number Long/parseLong) 3344)]
    (println "Starting on port: " port)
    (alter-var-root #'server
                    (fn [_]
                      (http/run-server ws-handler {:port port})))))
