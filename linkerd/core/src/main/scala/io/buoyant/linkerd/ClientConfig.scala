package io.buoyant.linkerd

import com.fasterxml.jackson.annotation.JsonIgnore
import com.twitter.conversions.time._
import com.twitter.finagle.Stack
import com.twitter.finagle.buoyant.{ClientAuth, ParamsMaybeWith, PathMatcher, TlsClientConfig => FTlsClientConfig}
import com.twitter.finagle.client.DefaultPool
import com.twitter.finagle.service._
import io.buoyant.router.RetryBudgetConfig
import io.buoyant.router.RetryBudgetModule.param

/**
 * ClientConfig is a trait containing protocol agnostic client configuration
 * options.  This trait can be mixed into a class to allow these options to be
 * set on that class as part of config deserialization.
 */
trait ClientConfig {

  var tls: Option[TlsClientConfig] = None
  var loadBalancer: Option[LoadBalancerConfig] = None
  var hostConnectionPool: Option[HostConnectionPool] = None
  var failFast: Option[Boolean] = None
  var failureAccrual: Option[FailureAccrualConfig] = None
  var requestAttemptTimeoutMs: Option[Int] = None
  var requeueBudget: Option[RetryBudgetConfig] = None
  var clientSession: Option[ClientSessionConfig] = None

  @JsonIgnore
  def params(vars: Map[String, String]): Stack.Params = Stack.Params.empty
    .maybeWith(tls.map(_.params(vars)))
    .maybeWith(loadBalancer.map(_.clientParams))
    .maybeWith(hostConnectionPool.map(_.param))
    .maybeWith(requestAttemptTimeoutMs.map(timeout => TimeoutFilter.Param(timeout.millis)))
    .maybeWith(failFast.map(FailFastFactory.FailFast(_)))
    .maybeWith(requeueBudget)
    .maybeWith(failureAccrual.map(FailureAccrualConfig.param))
    .maybeWith(clientSession.map(_.param))
}

case class TlsClientConfig(
  enabled: Option[Boolean],
  disableValidation: Option[Boolean],
  commonName: Option[String],
  trustCerts: Option[Seq[String]] = None,
  clientAuth: Option[ClientAuth] = None,
  protocols: Option[Seq[String]] = None
) {
  require(
    !disableValidation.getOrElse(false) || clientAuth.isEmpty,
    "disableValidation: true is incompatible with clientAuth"
  )
  def params(vars: Map[String, String]): Stack.Params =
    FTlsClientConfig(
      enabled,
      disableValidation,
      commonName.map(PathMatcher.substitute(vars, _)),
      trustCerts,
      clientAuth,
      protocols
    ).params
}

case class HostConnectionPool(
  minSize: Option[Int],
  maxSize: Option[Int],
  idleTimeMs: Option[Int],
  maxWaiters: Option[Int]
) {
  @JsonIgnore
  private[this] val default = DefaultPool.Param.param.default

  @JsonIgnore
  def param = DefaultPool.Param(
    low = minSize.getOrElse(default.low),
    high = maxSize.getOrElse(default.high),
    bufferSize = 0,
    idleTime = idleTimeMs.map(_.millis).getOrElse(default.idleTime),
    maxWaiters = maxWaiters.getOrElse(default.maxWaiters)
  )
}

case class ClientSessionConfig(
  lifeTimeMs: Option[Int],
  idleTimeMs: Option[Int]
) {
  @JsonIgnore
  private[this] val default = ExpiringService.Param.param.default

  @JsonIgnore
  def param = ExpiringService.Param(
    lifeTime = lifeTimeMs.map(_.millis).getOrElse(default.lifeTime),
    idleTime = idleTimeMs.map(_.millis).getOrElse(default.idleTime)
  )

}
