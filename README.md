trapperkeeper-tracing
=====================

[![Build Status](https://travis-ci.org/Sharpie/trapperkeeper-tracing.svg?branch=master)](https://travis-ci.org/Sharpie/trapperkeeper-tracing)

**NOTE:** This library is currently extremely experimental and is not suitable
for production use.

`trapperkeeper-tracing` is a library that provides a Clojure interface to the
[OpenTracing API for Java][opentracing]. This API allows TrapperKeeper services
to be instrumented in a manner that captures the performance of operations that
span multiple services.

  [opentracing]: https://github.com/opentracing/opentracing-java
