(ns sharpie.trapperkeeper.tracing.ring
  "Ring utilities for wrapping HTTP requests with OpenTracing contexts."
  (:require
    [sharpie.trapperkeeper.tracing :as tracing])
  (:import
    (io.opentracing.propagation Format$Builtin TextMapExtractAdapter)))


(defn wrap-with-tracing
  "Middleware to wrap requests with an OpenTracing context.

  This function takes a handler to be wrapped and a tracer instance
  and produces a new handler that extracts tracing information from
  request headers and then executes the wrapped handler inside of
  a trace context."
  [handler tracer]
  (fn [request]
    (let [headers (new TextMapExtractAdapter (:headers request))
          span-name (-> (:request-method request) name .toUpperCase)
          span-context (.extract tracer
                                 Format$Builtin/HTTP_HEADERS
                                 headers)]
      (binding [tracing/*tracer* tracer]
        (tracing/trace {:id span-name
                        :child-of span-context}
                       (handler request))))))
