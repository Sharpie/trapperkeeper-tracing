(ns sharpie.trapperkeeper.tracing.jaeger
  "Interface for jaegertracing.io.

  This namespace contains utilities for working with tracers from the
  Jaeger project. Jaeger provides a concrete implementation of operations
  defined by the OpenTracing API.

  See: https://jaegertracing.io"
  (:require
    [schema.core :as schema])
  (:import
    ;; NOTE: The Jaeger namespace may be changing to io.jaegertracing.jaeger.
    (com.uber.jaeger Tracer$Builder)
    (com.uber.jaeger.propagation CompositeCodec B3TextMapCodec TextMapCodec)
    (com.uber.jaeger.reporters NoopReporter)
    (com.uber.jaeger.samplers ConstSampler ProbabilisticSampler RateLimitingSampler)
    (io.opentracing.propagation Format$Builtin)))


(schema/defschema CodecConfiguration
  "An enumeration of valid Jaeger codecs to configure a Tracer with.

  Jaeger codecs are responsible for injecting and extracting trace
  data from carriers. For example, when extracting a parent span from
  a map of HTTP headers, the codec would determine which HTTP header
  names to use."
  [(schema/enum "b3" "jaeger")])

(schema/defschema ConstSamplerConfiguration
  "Hash structure for configuring a ConstSampler.

  ConstSampler instances are parameterized by a boolean which indicates
  whether every opertion is sampled."
  {:type (schema/eq "const")
   :param schema/Bool})

(schema/defschema ProbabilisticSamplerConfiguration
  "Hash structure for configuring a ProbabilisticSampler.

  ProbabilisticSampler instances are parameterized by a floating point
  value that indicates the probability a given operation will be sampled."
  {:type (schema/eq "probabilistic")
   :param java.lang.Double})

(schema/defschema RateLimitingSamplerConfiguration
  "Hash structure for configuring a RateLimitingSampler.

  RateLimitingSampler instances are parameterized by a floating point
  value that indicates the rate at which operations will be sampled."
  {:type (schema/eq "ratelimiting")
   :param java.lang.Double})

(schema/defschema SamplerConfiguration
  "Hash structure for configuring Jaeger Samplers."
  (schema/conditional
    #(= "const" (:type %)) ConstSamplerConfiguration
    #(= "probabilistic" (:type %)) ProbabilisticSamplerConfiguration
    #(= "ratelimiting" (:type %)) RateLimitingSamplerConfiguration))


(schema/defschema TracerConfiguration
  "Hash structure for configuring Jaeger Tracers.

  Patterned after the environment variables and properties used by the
  com.uber.jaeger.Configuration class."
  {:service-name schema/Str
   (schema/optional-key :codecs) CodecConfiguration
   (schema/optional-key :sampler) SamplerConfiguration})


(schema/defn ^:always-validate create-codecs
  "Creates map of codec instances from a CodecConfiguration."
  [config :- CodecConfiguration]
  (let [codecs (for [codec (set config)]
                 (case codec
                   ;; Generate pairs of Text and HTTP codecs.
                   "b3" [(new B3TextMapCodec) (new B3TextMapCodec)]
                   "jaeger" [(new TextMapCodec false) (new TextMapCodec true)]))
        arglists (apply (partial map vector) codecs)]
    (into {}
          (for [fmt [Format$Builtin/TEXT_MAP Format$Builtin/HTTP_HEADERS]
                args arglists]
            [fmt (new CompositeCodec args)]))))

(schema/defn ^:always-validate create-sampler
  "Creates a Jaeger sampler from a SamplerConfiguration."
  [config :- SamplerConfiguration]
  (let [param (:param config)]
    (case (:type config)
      "const" (new ConstSampler param)
      "probabilistic" (new ProbabilisticSampler param)
      "ratelimiting" (new RateLimitingSampler param))))


(schema/defn ^:always-validate create-tracer-builder
  "Generate a new Jaeger Configuration instance from a hash of configuration.

  The input and behavior of this function mostly follows that of the
  `com.uber.jaeger.Configuration` class, except that configuration data
  is explicitly passed as a map instead of being read from System properties
  and environment variables.

  The `Tracer$Builder` instance also has a few defaults that are different
  from what a Configuration instance would produce:

    - codecs: This function selects B3 as the default instead of using
              the Jaeger format. B3 headers are understood by a wider
              range of tracing systems such as Zipkin and Brave. The
              TraceContext specification being developed by the W3C
              will become the default once published.

    - sampler: This function configures a `ConstSampler` that is
               disabled by default.

    - reporter: This function defaults to configuring a `NoopReporter`.

  The defaults for sampler and reporter allow the `Tracer$Builder` instances
  produced by this class to be used in test cases that may swap in different
  components. A key feature of the ConstSampler and NoopReporter is that
  neither class allocates any state on initialization, such as a connection
  to a remote system, that must be cleaned up with a call to `.close`."
  [config :- TracerConfiguration]
  (let [builder (new Tracer$Builder (:service-name config))]
    ;; Configure Codecs
    (doseq [[fmt codec] (create-codecs (get config :codecs ["b3"]))]
      (.registerInjector builder fmt codec)
      (.registerExtractor builder fmt codec))
    ;; Configure Sampling
    (if-let [sampler-config (:sampler config)]
      (.withSampler builder (create-sampler sampler-config))
      (.withSampler builder (new ConstSampler false)))
    ;; Configure Reporting
    (if-let [sampler (:reporter config)]
      ;; TODO: Implement create-reporter
      (identity builder)
      (.withReporter builder (new NoopReporter)))))
