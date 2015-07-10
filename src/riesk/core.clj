(ns riesk.core
    (:require riesk.kibana 
              [riemann bin time]) 
		(:import org.elasticsearch.node.NodeBuilder))

(defonce es (atom nil))
(defn start-es [] (reset! es (.node (NodeBuilder/nodeBuilder))))
(defn stop-es [] (.close @es))

(defonce ki (atom nil))
(defn start-ki [] (reset! ki (riesk.kibana/start)))
(defn stop-ki [] (riesk.kibana/stop @ki))

(defn start-ri [] (riemann.bin/-main "config/riemann.config"))
(defn stop-ri [] (riemann.time/stop!))

(defn -main [& args]
   (case (first args)
     "stop" (do (stop-ki) (stop-es) (stop-ri))
     (do (start-es) (start-ki) (start-ri))))
