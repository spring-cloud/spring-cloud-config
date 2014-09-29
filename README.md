<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<!--[if IE]><meta http-equiv="X-UA-Compatible" content="IE=edge"><![endif]-->
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta name="generator" content="Asciidoctor 1.5.1">
<title>Spring Cloud Config</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Open+Sans:300,300italic,400,400italic,600,600italic|Noto+Serif:400,400italic,700,700italic|Droid+Sans+Mono:400">
<link rel="stylesheet" href="./asciidoctor.css">
</head>
<body class="article">
<div id="header">
<h1>Spring Cloud Config</h1>
</div>
<div id="content">
<div id="preamble">
<div class="sectionbody">
<div class="paragraph">
<p>Spring Cloud Config provides server and client-side support for
externalized configuration in a distributed system. With the Config
Server you have a central place to manage external properties for
applications across all environments. The concepts on both client and
server map identically to the Spring <code>Environment</code> and
<code>PropertySource</code> abstractions, so they fit very well with Spring
applications, but can be used with any application running in any
language. As an application moves through the deployment pipeline from
dev to test and into production you can manage the configuration
between those environments and be certain that applications have
everything they need to run when they migrate. The default
implementation of the server storage backend uses git so it easily
supports labelled versions of configuration environments, as well as
being accessible to a wide range of tooling for managing the content.
It is easy to add alternative implementations and plug them in with
Spring configuration.</p>
</div>
</div>
</div>
<div class="sect1">
<h2 id="_features">Features</h2>
<div class="sectionbody">
<div class="paragraph">
<p>Spring Cloud Config Server features:</p>
</div>
<div class="ulist">
<ul>
<li>
<p>HTTP, resource-based API for external configuration (name-value pairs, or equivalent YAML content)</p>
</li>
<li>
<p>Encrypt and decrypt property values (symmetric or asymmetric)</p>
</li>
<li>
<p>Embeddable easily in a Spring Boot application using <code>@EnableConfigServer</code></p>
</li>
</ul>
</div>
<div class="paragraph">
<p>Config Client features (for Spring applications):</p>
</div>
<div class="ulist">
<ul>
<li>
<p>Bind to the Config Server and initialize Spring <code>Environment</code> with remote property sources</p>
</li>
<li>
<p>Encrypt and decrypt property values (symmetric or asymmetric)</p>
</li>
<li>
<p><code>@RefreshScope</code> for Spring <code>@Beans</code> that want to be re-initialized when configuration changes</p>
</li>
<li>
<p>Management endpoints:</p>
<div class="ulist">
<ul>
<li>
<p><code>/env</code> for updating <code>Environment</code> and rebinding <code>@ConfigurationProperties</code> and log levels</p>
</li>
<li>
<p><code>/refresh</code> for refreshing the <code>@RefreshScope</code> beans</p>
</li>
<li>
<p><code>/restart</code> for restarting the Spring context (disabled by default)</p>
</li>
<li>
<p><code>/pause</code> and <code>/resume</code> for calling the <code>Lifecycle</code> methods (<code>stop()</code> and <code>start()</code> on the <code>ApplicationContext</code>)</p>
</li>
</ul>
</div>
</li>
<li>
<p>Bootstrap appplication context: a parent context for the main application that can be trained to do anything (by default it binds to the Config Server, and decrypts property values)</p>
</li>
</ul>
</div>
</div>
</div>
<div class="sect1">
<h2 id="_quick_start">Quick Start</h2>
<div class="sectionbody">
<div class="paragraph">
<p>Start the server:</p>
</div>
<div class="listingblock">
<div class="content">
<pre>$ cd spring-cloud-config-server
$ mvn spring-boot:run</pre>
</div>
</div>
<div class="paragraph">
<p>The server is a Spring Boot application so you can build the jar file
and run that (<code>java -jar &#8230;&#8203;</code>) or pull it down from a Maven
repository. Then try it out as a client:</p>
</div>
<div class="listingblock">
<div class="content">
<pre>$ curl localhost:8888/foo/development
{"name":"development","label":"master","propertySources":[
  {"name":"https://github.com/scratches/config-repo/foo-development.properties","source":{"bar":"spam"}},
  {"name":"https://github.com/scratches/config-repo/foo.properties","source":{"foo":"bar"}}
]}</pre>
</div>
</div>
<div class="paragraph">
<p>The default strategy for locating property sources is to clone a git
repository (at "spring.platform.config.server.uri") and use it to
initialize a mini <code>SpringApplication</code>. The mini-application&#8217;s
<code>Environment</code> is used to enumerate property sources and publish them
via a JSON endpoint. The service has resources in the form:</p>
</div>
<div class="listingblock">
<div class="content">
<pre>/{application}/{profile}[/{label}]</pre>
</div>
</div>
<div class="paragraph">
<p>where the "application" is injected as the "spring.config.name" in the
<code>SpringApplication</code> (i.e. what is normally "application" in a regular
Spring Boot app), "profile" is an active profile (or comma-separated
list of properties), and "label" is an optional git label (defaults to
"master").</p>
</div>
<div class="sect2">
<h3 id="_client_side_usage">Client Side Usage</h3>
<div class="paragraph">
<p>To use these features in an application, just build it as a Spring
Boot application that depends on spring-cloud-config-client
(e.g. see the test cases for the config-client, or the sample app).
When it runs it will pick up the external configuration from the
default local config server on port 8888 if it is running. To modify
the startup behaviour you can change the location of the config server
using <code>bootstrap.properties</code> (like <code>application.properties</code> but for
the bootstrap phase of an application context), e.g.</p>
</div>
<div class="listingblock">
<div class="content">
<pre>spring.platform.config.uri: http://myconfigserver.com</pre>
</div>
</div>
<div class="paragraph">
<p>The bootstrap properties will show up in the <code>/env</code> endpoint as a
high-priority property source, e.g.</p>
</div>
<div class="listingblock">
<div class="content">
<pre>$ curl localhost:8080/env
{
  "profiles":[],
  "configService:https://github.com/scratches/config-repo/bar.properties":{"foo":"bar"},
  "servletContextInitParams":{},
  "systemProperties":{...},
  ...
}</pre>
</div>
</div>
<div class="paragraph">
<p>(a property source called "configService:&lt;URL of remote
repository&gt;/&lt;file name&gt;" contains the property "foo" with value
"bar" and is highest priority).</p>
</div>
</div>
<div class="sect2">
<h3 id="_sample_application">Sample Application</h3>
<div class="paragraph">
<p>There is a sample application
<a href="https://github.com/spring-cloud/spring-cloud-config-sample">here</a>. It
is a Spring Boot application so you can run it using the usual
mechanisms (for instance "mvn spring-boot:run"). When it runs it will
look for the config server on "http://localhost:8888" by default, so
you could run the server as well to see it all working together.</p>
</div>
<div class="paragraph">
<p>The sample has a test case where the config server is also started in
the same JVM (with a different port), and the test asserts that an
environment property from the git configuration repo is present. To
change the location of the config server just set
"spring.platform.config.uri" in "bootstrap.yml" (or via System
properties etc.).</p>
</div>
<div class="paragraph">
<p>The test case has a <code>main()</code> method that runs the server in the same
way (watch the logs for its port), so you can run the whole system in
one process and play with it (e.g. right click on the main in your IDE
and run it). The <code>main()</code> method uses <code>target/config</code> for the working
directory of the git repository, so you can make local changes there
and see them reflected in the running app.</p>
</div>
<div class="listingblock">
<div class="content">
<pre>$ curl localhost:8080/env/foo
bar
$ vi target/config/bar.properties
.. change value of "foo", optionally commit
$ curl localhost:8080/refresh
["foo"]
$ curl localhost:8080/env/foo
baz</pre>
</div>
</div>
<div class="paragraph">
<p>The refresh endpoint reports that the "foo" property changed.</p>
</div>
</div>
</div>
</div>
</div>
<div id="footer">
<div id="footer-text">
Last updated 2014-09-26 15:07:18 BST
</div>
</div>
</body>
</html>