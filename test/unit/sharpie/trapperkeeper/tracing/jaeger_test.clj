(ns sharpie.trapperkeeper.tracing.jaeger-test
  (:require
    [clojure.test :refer :all]
    [sharpie.trapperkeeper.tracing :as tracing]
    [sharpie.trapperkeeper.tracing.jaeger :as jaeger])
  (:import
    (com.uber.jaeger.reporters InMemoryReporter)
    (io.opentracing.propagation Format$Builtin TextMapExtractAdapter)))


(def reporter (new InMemoryReporter))
(def tracer (-> (jaeger/create-tracer-builder {:service-name "jaeger-test"})
                (.withReporter reporter)
                .build))


(deftest tracer-jaeger-b3-test
  (testing "B3 headers are extracted to a span context"
    (let [headers (new TextMapExtractAdapter
                       {"x-b3-traceid" "00000000000000000000000000002328"
                        "x-b3-spanid" "000000000000002a"
                        "x-b3-parentspanid" "0"
                        "x-b3-sampled" "true"
                        "x-b3-flags" "1"})
          context (.extract tracer
                            Format$Builtin/HTTP_HEADERS
                            headers)]
      (is (= 9000 (.getTraceId context)))
      (is (= 42 (.getSpanId context)))
      (is (= 0 (.getParentId context)))
      (is (.isSampled context))
      (is (.isDebug context)))))
