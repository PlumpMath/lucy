class leiningen {
  
  include java
  
  package { "wget":
    ensure => installed,
  }
  ->
  exec{'retrieve_leiningen':
    command => "/usr/bin/wget -q https://raw.github.com/technomancy/leiningen/stable/bin/lein -O /usr/local/bin/lein",
    creates => "/usr/local/bin/lein",
    require => Class['java'],
  }
  ->
  file{'/usr/local/bin/lein':
    mode => 0755,
    ensure => present,
  }

}
