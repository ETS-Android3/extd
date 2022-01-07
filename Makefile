.POSIX:

include config.mk

clean:
	rm -f *.o *.gch
install:
	useradd --system -d $(EXTD_USER_HOME) -m extd
	# echo 'extd:1234k' | chpasswd
	mkdir -p $(BIN_DIR)
	mkdir -p $(SYSTEMD_USER_DIR)
	mkdir -p $(EXTD_USER_HOME)/bin

	chmod -R 755 $(BIN_DIR)

	mkdir $(EXTD_USER_HOME)/.ssh
	chmod 700 $(EXTD_USER_HOME)/.ssh
	touch $(EXTD_USER_HOME)/.ssh/authorized_keys
	chmod 600 $(EXTD_USER_HOME)/.ssh/authorized_keys
	echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQC4os0Tt98S8xzYzCPZR5KxM19Kx/GdgFMskZNDbJufOkpvoTNhYEVwT4g/2IvLjc0vJAhGPatBKPq/ce7hzeMsnAvS4AXKZWT9f0DY74oVlSk/slYcEjwv+mxWsXWPszUfpRnVDBGA6IAb9jvPgzn2lo2eNPGpUMjk3JmGajy/vHsxtpAejDd9WDnxtJOBEwFk+0cCL/uqakSArLN0BVfnuWBrV/Xx25FsT3feInj3UPintJz72QhzmiwVbyTlJxQhZUg4w6QiGI/IOG9vMPJSW6hHfbp1ala0F6JqF4UV1HS+y3i6M0q8Otb3QCpCc7MCtU+IMBGCuZIKc4CfNW6y6iqXwhhJV7A8X2v7dTB3ftqceoKdVVPNzILNxPUZ7OTDLWQjHgbupNcIGsiFLn6PF9EUYPrt6B3OTvnz3LM4eGez1keCfZpF9y8QVMcPosckTiM364E/IdVl7xVAASdXWGANnbn/LLuG4bG6+LLcS4pD02zcABgGM9+9jdo+Gnefj6+mcMLxZJPAUF761K5ojm9UN9WhDIvqMQoPlc3YR5O7MyYuYd1b7nX2YLfD8Jsn6APcrgXtgLnVQOfohr1sniJWx6yrqPh2mWZ5Eg7TG5JGkWxQ6B7O7VTNpfngPQWNjfmavYo9hOjNpFWhQ8TPnQZaXI4NqIrVrjbQM0gUAw== me@archbook" > $(EXTD_USER_HOME)/.ssh/authorized_keys

	sed 's#__BIN_DIR__#$(BIN_DIR)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd.service > $(SYSTEMD_USER_DIR)/extd.service
	sed 's#__USER_HOME_DIR__#$(EXTD_USER_HOME)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd_manager.py > $(BIN_DIR)/extd_manager
	sed 's#__USER_HOME_DIR__#$(EXTD_USER_HOME)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd_daemon.py > $(BIN_DIR)/extd_daemon
	sed 's#__USER_HOME_DIR__#$(EXTD_USER_HOME)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' ssh_handler.py > $(EXTD_USER_HOME)/bin/ssh_handler

	chown -R extd:extd $(EXTD_USER_HOME)
	chown $(USER):$(USER) $(BIN_DIR)/extd_manager
	chown $(USER):$(USER) $(BIN_DIR)/extd_daemon
	chown $(USER):$(USER) $(SYSTEMD_USER_DIR)/extd.service

	chmod 755 $(BIN_DIR)/extd_manager
	chmod 755 $(BIN_DIR)/extd_daemon
	chmod +x $(EXTD_USER_HOME)/bin/ssh_handler

	# set ssh_handler as extd user's shell
	usermod -s $(EXTD_USER_HOME)/bin/ssh_handler extd

uninstall:
	rm -f $(BIN_DIR)/extd_manager || echo "already clean"
	rm -f $(BIN_DIR)/extd_daemon || echo "already clean"
	rm -rf $(EXTD_USER_HOME) || echo "already clean"

	usermod -G "" extd || echo "no use or group"
	userdel -f -r extd || echo "no user"
	groupdel extd || echo "no group"

.PHONY: clean uninstall install
