(ns dev-tools
  "Development tools for TrapperKeeper apps."
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :as pprint :refer [pp pprint]]
    [clojure.reflect :as reflect]
    [clojure.repl :refer :all]

    [clojure.tools.namespace.repl :as ns-tools]

    ;; Core TrapperKeeper libs
    [puppetlabs.trapperkeeper.core :as tk]
    [puppetlabs.trapperkeeper.app :as tk-app]
    [puppetlabs.trapperkeeper.bootstrap :as tk-bootstrap]
    [puppetlabs.trapperkeeper.config :as tk-config]
    [puppetlabs.trapperkeeper.services :as tk-service]

    ;; TrapperKeeper extension libs
    [puppetlabs.trapperkeeper.services.webserver.jetty9-service :as tk-jetty]
    [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :as tk-routing]))

(ns-tools/set-refresh-dirs "dev" "src/clj")


;; Functions for managing TK App lifecycle

(def system
  "Refrence to TrapperKeeper app.

  Holds a reference to the TrapperKeeper app currently running under
  the REPL."
  (atom {:state :empty :app nil}))

(defn init-tk
  "Initialize a new TrapperKeeper app.

  Populates the `system` var if successful. Throws an IllegalStateError
  if `system` is already populated."
  []
  (if (= :empty (:state @system))
    (let [app (tk/build-app
                (tk-bootstrap/parse-bootstrap-config!
                  "dev-resources/trapperkeeper-tracing/bootstrap.cfg")
                (tk-config/load-config
                  "dev-resources/trapperkeeper-tracing/conf.d"))]
      (tk-app/init app)
      (tk-app/check-for-errors! app)
      (swap! system (constantly {:state :initialized :app app})))
    (throw (new IllegalStateException "The system var is currently set to a non-nil value. Call dev-tools/destroy-tk to discard system state before initializing a new app instance."))))

(defn start-tk
  "Start TrapperKeeper app.

  Starts the TrapperKeeper app referenced by the `system` var.
  Calls `init-tk` to generate a new app if `system` is not initialized."
  []
  (when (= :empty (:state @system))
    (init-tk))
  (swap! system
         (fn [{:keys [state app] :as sys}]
           (cond
             (= :initialized state)
               (do
                 (tk-app/start app)
                 (tk-app/check-for-errors! app)

                 {:state :running :app app})

             (= :stopped state)
                ;; FIXME: Something is wrong with stop/restart. After a
                ;; restart, triggering stop kicks off the Stop lifecycle,
                ;; but nothing actually happens. Which means services
                ;; like webservers are left holding onto resources such
                ;; as ports.
               (do
                 (tk-app/restart app)
                 (tk-app/check-for-errors! app)

                 {:state :running :app app})

             ;; else, return original system unmodified.
             :else sys))))

(defn stop-tk
  "Stop TrapperKeeper app.

  Stops the TrapperKeeper app referenced by the `system` var."
  []
  (swap! system
         (fn [{:keys [state app] :as sys}]
           (if-not (contains? #{:stopped :empty} state)
             (do
              (tk-app/stop app)
              {:state :stopped :app app})

             ;; else, return original system unmodified.
             sys))))

(defn destroy-tk
  "Stop and discard TrapperKeeper app.

  Stops the TrapperKeeper app referenced by the `system` var and
  then sets `system` to nil so that a new app may be created."
  []
  (swap! system
         (fn [{:keys [state app] :as sys}]
           (if-not (= :empty state)
             (do
               (when-not (= :stopped state)
                 (tk-app/stop app))

               {:state :empty :app nil})

             sys))))

(defn reload-tk
  "Refresh Clojure source code and build a new TrapperKeeper app.

  Stops and discards any TrapperKeeper app referenced by the `system`
  var, then refreshes Clojure source code and starts a new app
  via `start-tk`."
  []
  (destroy-tk)
  (ns-tools/refresh :after 'dev-tools/start-tk))


;; Helpers for inspecting TK app state

(defn get-tk
  "Get TrapperKeeper instances.

  When called with no arguments, returns the TrapperkeeperApp instance
  from the `system`.

  When called with one argument, a service name, returns the Service
  instance from the `system`."
  ([]
   (:app @system))

  ([service-name]
   (-> (:app @system)
       (tk-app/get-service service-name))))

(defn ls-tk
  "Pretty-print TrapperKeeper state.

  When called with no arguments, lists the names ofall TrapperKeeper services
  in the current `system`.

  When called with one argument, a service name, pretty-prints the state
  of the given service."
  ([]
   (-> (get-tk)
       tk-app/service-graph
       keys
       pprint))

  ([service-name]
   (-> (get-tk service-name)
       tk-service/service-context
       pprint)))
