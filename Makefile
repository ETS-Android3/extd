.POSIX:

include config.mk

clean:
	rm -f *.o *.gch
install:
	useradd --system -d $(DESTDIR)$(PREFIX) -m extd
	# echo 'extd:1234k' | chpasswd
	chmod 755 $(DESTDIR)$(PREFIX)

	find $(DESTDIR)$(PREFIX) -name ".*" -exec rm -rf {} +

	mkdir $(DESTDIR)$(PREFIX)/.ssh
	chmod 700 $(DESTDIR)$(PREFIX)/.ssh
	touch $(DESTDIR)$(PREFIX)/.ssh/authorized_keys
	chmod 600 $(DESTDIR)$(PREFIX)/.ssh/authorized_keys
	echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQC4os0Tt98S8xzYzCPZR5KxM19Kx/GdgFMskZNDbJufOkpvoTNhYEVwT4g/2IvLjc0vJAhGPatBKPq/ce7hzeMsnAvS4AXKZWT9f0DY74oVlSk/slYcEjwv+mxWsXWPszUfpRnVDBGA6IAb9jvPgzn2lo2eNPGpUMjk3JmGajy/vHsxtpAejDd9WDnxtJOBEwFk+0cCL/uqakSArLN0BVfnuWBrV/Xx25FsT3feInj3UPintJz72QhzmiwVbyTlJxQhZUg4w6QiGI/IOG9vMPJSW6hHfbp1ala0F6JqF4UV1HS+y3i6M0q8Otb3QCpCc7MCtU+IMBGCuZIKc4CfNW6y6iqXwhhJV7A8X2v7dTB3ftqceoKdVVPNzILNxPUZ7OTDLWQjHgbupNcIGsiFLn6PF9EUYPrt6B3OTvnz3LM4eGez1keCfZpF9y8QVMcPosckTiM364E/IdVl7xVAASdXWGANnbn/LLuG4bG6+LLcS4pD02zcABgGM9+9jdo+Gnefj6+mcMLxZJPAUF761K5ojm9UN9WhDIvqMQoPlc3YR5O7MyYuYd1b7nX2YLfD8Jsn6APcrgXtgLnVQOfohr1sniJWx6yrqPh2mWZ5Eg7TG5JGkWxQ6B7O7VTNpfngPQWNjfmavYo9hOjNpFWhQ8TPnQZaXI4NqIrVrjbQM0gUAw== me@archbook" > $(DESTDIR)$(PREFIX)/.ssh/authorized_keys

	mkdir -p $(DESTDIR)$(PREFIX)/bin
	sed 's#__BASE_DIR__#$(DESTDIR)$(PREFIX)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd.service > $(SYSTEMD_SERVICE_DIR)/extd.service
	sed 's#__BASE_DIR__#$(DESTDIR)$(PREFIX)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd_manager.py > /usr/bin/extd_manager
	sed 's#__BASE_DIR__#$(DESTDIR)$(PREFIX)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd_daemon.py > $(DESTDIR)$(PREFIX)/bin/extd_daemon
	sed 's#__BASE_DIR__#$(DESTDIR)$(PREFIX)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' ssh_handler.py > $(DESTDIR)$(PREFIX)/bin/ssh_handler

	chown -R extd:extd $(DESTDIR)$(PREFIX)
	chown extd:extd /usr/bin/extd_manager

	chmod -R 755 $(DESTDIR)$(PREFIX)/bin
	chmod 755 /usr/bin/extd_manager

	usermod -s $(DESTDIR)$(PREFIX)/bin/ssh_handler extd

uninstall:
	rm -f /usr/bin/extd_manager
	rm -rf $(DESTDIR)$(PREFIX)

	usermod -G "" extd || echo "no use or group"
	userdel -f -r extd || echo "no user"
	groupdel extd || echo "no group"

.PHONY: clean install uninstall
