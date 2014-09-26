require 'asciidoctor'
require 'erb'

options = {:to_dir => 'target/docs', :mkdirs => true, :safe => :unsafe, :attributes => 'linkcss'}

guard 'shell' do
  watch(/^[A-Za-z].*\.adoc$/) {|m|
    Asciidoctor.render_file('README.adoc', options)
    Asciidoctor.render_file('src/main/adoc/spring-cloud-config.adoc', options)
  }
end
