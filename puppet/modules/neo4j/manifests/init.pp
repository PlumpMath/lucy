class neo4j {

  include java
  
  apt::source { 'neo4j':
    location          => 'http://debian.neo4j.org/repo',
    release           => 'stable/',
    repos             => '',
    required_packages => 'debian-keyring debian-archive-keyring',
    key               => '2DC499C3',
    key_source        => 'http://debian.neo4j.org/neotechnology.gpg.key',
  }
  ->
  package { 'neo4j':
    ensure  => installed,
    require => Class['java'],
  }
  ->
  file { "/etc/neo4j/neo4j-server.properties":
    mode => 644,
    owner => neo4j,
    group => adm,
    source => "puppet:///modules/neo4j/neo4j-server.properties",
  }
  ->
  service { 'neo4j-service' :
    ensure     => running,
    enable     => true,
    hasrestart => true,
    hasstatus  => true,
  }

  # TODO: Harden access by following this - https://joewhite86.wordpress.com/2013/05/29/secure-neo4j-webadmin-using-http-auth-and-ssl/

}
