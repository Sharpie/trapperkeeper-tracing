(ns sharpie.trapperkeeper.tracing-test
  (:require
    [clojure.test :refer :all]
    [sharpie.trapperkeeper.tracing :as tracing])
  (:import
    (io.opentracing.mock MockSpan$MockContext MockTracer)
    (io.opentracing.noop NoopTracerFactory)))

(def noop-tracer (NoopTracerFactory/create))
(def mock-tracer (new MockTracer))


(deftest initial-tracer-test
  (testing "The *tracer* var is initially set to an instance of NoopTracer"
    (is (= noop-tracer tracing/*tracer*))))

(deftest set-tracer-test
  (testing "Calling set-tracer! updates the *tracer* var"
    (tracing/set-tracer! mock-tracer)
    (is (= mock-tracer tracing/*tracer*)))

  (testing "Calling set-tracer! a second time raises an error"
    (is (thrown? IllegalStateException (tracing/set-tracer! (new MockTracer))))))


(deftest build-span-test
  (binding [tracing/*tracer* mock-tracer]
    (testing "Calling build-span with an operation name produces a span with that name."
      (let [span (tracing/build-span "test span")]
        (is (= "test span" (.operationName span)))))

    (testing "Calling build-span with a parent context produces a span that is a child of that parent."
      (let [parent-context (new MockSpan$MockContext
                                (long 9000)
                                (long 42)
                                {"test-baggage-item" "foo"})
            span (tracing/build-span "test span with parent"
                                     {:child-of parent-context})]

        (is (= 9000 (-> span .context .traceId)))
        (is (= 42 (.parentId span)))
        (is (= "foo" (.getBaggageItem span "test-baggage-item")))))

    (testing "Calling build-span with tags produces a tagged span."
      (let [span (tracing/build-span "test span with tags"
                                     {:tags {"foo" "bar"
                                             "baz" 42
                                             "froob" false}})
            tags (.tags span)]
        (is (= "bar" (get tags "foo")))
        (is (= 42 (get tags "baz")))
        (is (false? (get tags "froob")))))))


(deftest trace-test
  (binding [tracing/*tracer* mock-tracer]
    (testing "Traced operations record a span"
      (tracing/trace "test trace 1" :foo)
      (tracing/trace "test trace 2"
        (tracing/trace "test trace 3" :bar))

      (let [spans (.finishedSpans tracing/*tracer*)
            _ (.reset tracing/*tracer*)]

        (is (= 3 (count spans)))
        (is (= ["test trace 1" "test trace 3" "test trace 2"]
               (map #(.operationName %) spans)))))

    (testing "Traced opertions record execution time"
      (tracing/trace "test trace 1" (Thread/sleep 500))
        (let [span (-> tracing/*tracer* .finishedSpans first)
              _ (.reset tracing/*tracer*)]
          (is (>= (- (.finishMicros span) (.startMicros span))
                (long 0.5e6)))))

    (testing "Span options can be set by passing a map to trace"
      (tracing/trace {:id "test trace with options"
                      :child-of (new MockSpan$MockContext
                                     (long 8140)
                                     (long 66)
                                     {"bar-baggage-item" "bar"})}
                     :noop)
        (let [span (-> tracing/*tracer* .finishedSpans first)
              _ (.reset tracing/*tracer*)]
          (is (= "test trace with options" (.operationName span)))
          (is (= 8140 (-> span .context .traceId)))
          (is (= 66 (.parentId span)))
          (is (= "bar" (.getBaggageItem span "bar-baggage-item")))))))
