require 'asciidoctor'
require 'erb'

options = {:mkdirs => true, :safe => :unsafe, :attributes => 'linkcss'}

guard 'shell' do
  watch(/^[A-Za-z].*\.adoc$/) {|m|
    Asciidoctor.render_file('src/main/adoc/README.adoc', options.merge(:to_dir => '.'))
    Asciidoctor.render_file('src/main/adoc/spring-cloud-config.adoc', options.merge(:to_dir => 'target/docs'))
  }
end
