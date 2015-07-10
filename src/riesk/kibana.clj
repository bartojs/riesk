(ns riesk.kibana
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [files not-found]]
            [compojure.handler :refer [site]]
            [ring.adapter.jetty :refer [run-jetty]]))
  
 (defroutes routes
    (files "/")
    (not-found "Not Found"))

 (defn start [& {:keys [host port join?] :or {host "0.0.0.0" port 9090 join? false}}]
    (run-jetty (site routes) {:host host :port port :join? join?}))

 (defn stop [server]
    (when server (.stop server)))
