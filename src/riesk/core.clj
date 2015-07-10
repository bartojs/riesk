(ns riesk.core
    (:require riesk.kibana 
              [riemann bin time]) 
		(:import org.elasticsearch.node.NodeBuilder
             org.elasticsearch.common.settings.ImmutableSettings))

(defonce es (atom nil))
(defn start-es [] (reset! es (.node (doto (NodeBuilder/nodeBuilder) (.loadConfigSettings false) (.settings (.loadFromSource (ImmutableSettings/settingsBuilder) (slurp "config/elasticsearch.properties")))))))
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
