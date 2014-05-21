# -*- mode: ruby -*-
# vi: set ft=ruby :

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  
  # Use Ubuntu 14.04 so we can deploy to EC2/Digital Ocean
  # EC2: https://help.ubuntu.com/community/EC2StartersGuide
  # Digital Ocean: https://www.digitalocean.com/company/blog/announcing-the-release-of-our-ubuntu-1404LTS-image/
  config.vm.box = "http://cloud-images.ubuntu.com/vagrant/trusty/current/trusty-server-cloudimg-amd64-vagrant-disk1.box"

  # Provision Puppet Modules
  config.vm.provision :shell do |shell|
    shell.inline = "
                    mkdir -p /etc/puppet/modules;
                    ruby gem puppet-module;
                    (puppet module install stankevich-python; true);
                    (puppet module install puppetlabs-apt; true);
                   "
  end

  # Provision Using Puppet
  config.vm.provision :puppet do |puppet|
    puppet.manifests_path = "puppet/manifests"
    puppet.module_path = ["puppet/modules/"]
    puppet.options = ['--verbose']
  end

  # Forward ports
  config.vm.network "forwarded_port", guest: 7474, host: 7474 # Neo4J
end
