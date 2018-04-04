(ns sharpie.trapperkeeper.tracing
  "Interface to the OpenTracing API.

  The functions in this namespace allow Clojure to interact with the
  OpenTracing API."
  (:require
    [schema.core :as schema])
  (:import
    (io.opentracing Tracer)
    (io.opentracing.noop NoopTracerFactory)
    (io.opentracing.util GlobalTracer)))


(def ^:dynamic *tracer* (NoopTracerFactory/create))


(schema/defn set-tracer!
  "Set a new Tracer instance as the global tracer.

  This function updates the tracer instance referenced by the
  `io.opentracing.GlobalTracer` class along with the root of
  the `*tracer*` var.

  This function may be called exactly once. Multiple attempts
  to set the tracer will result in an error being raised."
  [tracer :- Tracer]
  ;; NOTE: GlobalTracer starts out with an instance of NoopTracer which
  ;;       can be replaced exactly once with a different Tracer instance.
  ;;       Attempting to register a second Tracer results in an error
  ;;       being raised.
  (GlobalTracer/register tracer)
  (alter-var-root #'*tracer* (constantly tracer)))

