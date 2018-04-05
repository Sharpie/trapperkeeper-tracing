(ns sharpie.trapperkeeper.tracing-test
  (:require
    [clojure.test :refer :all]
    [sharpie.trapperkeeper.tracing :as tracing])
  (:import
    (io.opentracing.mock MockTracer)
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
        (is (= "test span" (.operationName span)))))))
