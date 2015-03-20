require 'asciidoctor'
require 'erb'

options = {:mkdirs => true, :safe => :unsafe, :attributes => ['linkcss', 'allow-uri-read'] }

guard 'shell' do
  watch(/^docs\/[A-Za-z][^#]*\.adoc$/) {|m|
     Asciidoctor.load_file('docs/src/main/asciidoc/README.adoc', :to_file => './README.adoc', safe: :safe, parse: false, attributes: 'allow-uri-read')
    Asciidoctor.render_file('docs/src/main/asciidoc/spring-cloud-config.adoc', options.merge(:to_dir => './docs/target/generated-docs'))
  }
end
