(def opentracing-version "0.31.0")


(defproject org.clojars.sharpie/trapperkeeper-tracing "0.0.1"
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
                 [io.opentracing/opentracing-noop ~opentracing-version]]

  :profiles {:dev {:dependencies [[io.opentracing/opentracing-mock ~opentracing-version]
                                  [ring-mock]]}})
