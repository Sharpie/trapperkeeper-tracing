(def opentracing-version "0.31.0")
(def jaeger-version "0.26.0")


(defproject org.clojars.sharpie/trapperkeeper-tracing "0.0.1-SNAPSHOT"
  :description "OpenTracing API bindings for use with the puppetlabs/trapperkeeper service framework."
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :plugins [[lein-parent "0.3.4"]]

  :parent-project {:coords [puppetlabs/clj-parent "1.7.4"]
                   :inherit [:managed-dependencies]}

  :source-paths ["src/clj"]
  :test-paths ["test/unit"]

  :dependencies [[org.clojure/clojure]

                 [puppetlabs/trapperkeeper]

                 [ring/ring-core]

                 [io.opentracing/opentracing-api ~opentracing-version]
                 [io.opentracing/opentracing-util ~opentracing-version]
                 [io.opentracing/opentracing-noop ~opentracing-version]

                 [com.uber.jaeger/jaeger-core ~jaeger-version]
                 ;; Shaded build of Thrift 0.9.2 used by Jaeger.  Frees other
                 ;; components to bring in a newer Thrift version if needed.
                 [com.uber.jaeger/jaeger-thrift ~jaeger-version :classifier "thrift92"]]

  :profiles {:dev {:dependencies [[io.opentracing/opentracing-mock ~opentracing-version]
                                  [ring-mock]]}})
