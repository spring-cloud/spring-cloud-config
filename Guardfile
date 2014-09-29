require 'asciidoctor'
require 'erb'

options = {:mkdirs => true, :safe => :unsafe, :attributes => 'linkcss'}

guard 'shell' do
  watch(/^[A-Za-z].*\.adoc$/) {|m|
    Asciidoctor.render_file('src/main/asciidoc/README.adoc', options.merge(:to_file => './README.md'))
    Asciidoctor.render_file('src/main/asciidoc/spring-cloud-config.adoc', options.merge(:to_dir => 'target/generated-docs'))
  }
end
