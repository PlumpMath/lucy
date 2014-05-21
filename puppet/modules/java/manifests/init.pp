class java {

  # OpenJDK
  #$java_packages = ["default-jre-headless", "default-jdk"]

  # Oracle Java 8
  $java_packages = ["oracle-java8-installer"]

  $license = "oracle-license-v1-1"
  apt::source { 'oracle-jdk':
    location          => 'http://ppa.launchpad.net/webupd8team/java/ubuntu',
    release           => 'trusty',
    repos             => 'main',
    required_packages => 'debian-keyring debian-archive-keyring',
    key               => 'EEA14886',
    key_source        => 'http://keyserver.ubuntu.com:11371/pks/lookup?op=get&search=0xC2518248EEA14886',
  }
  -> exec { "Accept Oracle License":
    # http://askubuntu.com/a/190674
    command => "echo debconf shared/accepted-${license} select true | debconf-set-selections ; echo debconf shared/accepted-${license} seen true | debconf-set-selections",
    unless => ["debconf-get-selections | grep shared/accepted-${license}"],
  }
  -> package { $java_packages: ensure => installed }
}
