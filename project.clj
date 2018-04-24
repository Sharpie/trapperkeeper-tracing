(def opentracing-version "0.31.0")
(def jaeger-version "0.27.0")


(defproject org.clojars.sharpie/trapperkeeper-tracing "0.0.1-SNAPSHOT"
  :description "OpenTracing API bindings for use with the puppetlabs/trapperkeeper service framework."
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :plugins [[lein-parent "0.3.4"]]

  :parent-project {:coords [puppetlabs/clj-parent "1.7.5"]
                   :inherit [:managed-dependencies]}

  :source-paths ["src/clj"]
  :test-paths ["test/unit"]

  :dependencies [[org.clojure/clojure]

                 [puppetlabs/trapperkeeper]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/trapperkeeper-webserver-jetty9]

                 [ring/ring-core]

                 [io.opentracing/opentracing-api ~opentracing-version]
                 [io.opentracing/opentracing-util ~opentracing-version]
                 [io.opentracing/opentracing-noop ~opentracing-version]

                 [io.jaegertracing/jaeger-core ~jaeger-version
                  ;; slf4j is managed by clj-parent.
                  :exclusions [org.slf4j/slf4j-api]]
                 ;; NOTE: jaeger-thrift currently has a hard dependency
                 ;;       on v0.9.2 of Thrift.
                 [io.jaegertracing/jaeger-thrift ~jaeger-version
                  ;; httpclient is managed by clj-parent.
                  :exclusions [org.apache.httpcomponents/httpclient
                               org.apache.httpcomponents/httpcore]]]

  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]
                   :repl-options {:init-ns dev-tools}
                   :dependencies [[org.clojure/tools.namespace]
                                  [io.opentracing/opentracing-mock ~opentracing-version]
                                  [ring-mock]]}})
