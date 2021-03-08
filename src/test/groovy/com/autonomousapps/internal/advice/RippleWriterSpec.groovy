package com.autonomousapps.internal.advice

import com.autonomousapps.advice.Advice
import com.autonomousapps.advice.Dependency
import com.autonomousapps.advice.Pebble
import com.autonomousapps.advice.Pebble.Ripple
import com.autonomousapps.advice.TransitiveDependency
import spock.lang.Specification

import static com.google.common.truth.Truth.assertThat

class RippleWriterSpec extends Specification {

  def "no ripples"() {
    given:
    def pebble = new Pebble(':a', [] as Set<Ripple>)

    when:
    def msg = new RippleWriter(pebble).buildMessage()

    then:
    assertThat(msg).isEqualTo("Project :a contains no potential ripples.")
  }

  def "one ripple"() {
    given:
    def ripple = new Ripple(
      ':app',
      Advice.ofRemove(apiDependency('com.foo:bar')),
      Advice.ofAdd(trans('com.foo:bar'), 'implementation')
    )
    def pebble = new Pebble(':core', [ripple] as Set<Ripple>)

    when:
    def msg = new RippleWriter(pebble).buildMessage()

    then:
    assertThat(removeColors(msg)).isEqualTo("""\
    Ripples:
    - You have been advised to make a change to :core that might impact dependent projects
      - Remove com.foo:bar from 'api'
        :app uses this dependency transitively. You should add it to 'implementation'
    """.stripIndent())
  }

  def "one upstream project with multiple downstream impacts relating to a single dependency"() {
    given:
    def ripples = [] as Set<Ripple>
    ripples += new Ripple(
      ':app',
      Advice.ofRemove(apiDependency('com.foo:bar')),
      Advice.ofAdd(trans('com.foo:bar'), 'implementation')
    )
    ripples += new Ripple(
      ':other',
      Advice.ofRemove(apiDependency('com.foo:bar')),
      Advice.ofAdd(trans('com.foo:bar'), 'implementation')
    )
    def pebble = new Pebble(':core', ripples)

    when:
    def msg = new RippleWriter(pebble).buildMessage()

    then:
    assertThat(removeColors(msg)).isEqualTo("""\
    Ripples:
    - You have been advised to make a change to :core that might impact dependent projects
      - Remove com.foo:bar from 'api'
        :app uses this dependency transitively. You should add it to 'implementation'
        :other uses this dependency transitively. You should add it to 'implementation'
    """.stripIndent())
  }

  def "one upstream project with multiple downstream impacts relating to a two dependencies"() {
    given:
    def ripples = [] as Set<Ripple>
    ripples += new Ripple(
      ':app',
      Advice.ofRemove(apiDependency('com.foo:bar')),
      Advice.ofAdd(trans('com.foo:bar'), 'implementation')
    )
    ripples += new Ripple(
      ':other',
      Advice.ofRemove(apiDependency('com.bar:baz')),
      Advice.ofAdd(trans('com.bar:baz'), 'implementation')
    )
    def pebble = new Pebble(':core', ripples)

    when:
    def msg = new RippleWriter(pebble).buildMessage()

    then:
    assertThat(removeColors(msg)).isEqualTo("""\
    Ripples:
    - You have been advised to make a change to :core that might impact dependent projects
      - Remove com.foo:bar from 'api'
        :app uses this dependency transitively. You should add it to 'implementation'
      - Remove com.bar:baz from 'api'
        :other uses this dependency transitively. You should add it to 'implementation'
    """.stripIndent())
  }

  private static Dependency undeclaredDependency(String id) {
    return new Dependency(id, null, null)
  }

  private static Dependency implDependency(String id) {
    return new Dependency(id, null, "implementation")
  }

  private static Dependency apiDependency(String id) {
    return new Dependency(id, null, "api")
  }

  private static TransitiveDependency trans(String id) {
    return new TransitiveDependency(undeclaredDependency(id), [] as Set<Dependency>, [] as Set<String>)
  }

  private static String removeColors(String s) {
    return s.replaceAll("\u001B\\[.+?m", "")
  }
}
