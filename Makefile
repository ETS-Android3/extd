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
	
	sudo -u $(USER) ssh-keygen -t rsa -b 4096 -f $(USER_KEY_FOLDER)/extd -N "" 0>&-
	sudo -u $(USER) python3 gen_key.py $(USER_KEY_FOLDER)/extd.key

	chmod 600 $(USER_KEY_FOLDER)/extd.key

	# copy the key to extd, so extd user will be able to decrypt and encrypt
	cp $(USER_KEY_FOLDER)/extd.key $(EXTD_USER_HOME)/.ssh/extd.key

	cat "$(USER_KEY_FOLDER)/extd.pub" >> $(EXTD_USER_HOME)/.ssh/authorized_keys

	sed 's#__BIN_DIR__#$(BIN_DIR)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd.service > $(SYSTEMD_USER_DIR)/extd.service
	
	sed 's#__PRIVATE_KEY__#$(USER_KEY_FOLDER)/extd.key#g;s#__USER_HOME_DIR__#$(EXTD_USER_HOME)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' key_utils.py > $(BIN_DIR)/key_utils.py

	sed 's#__PRIVATE_KEY__#$(USER_KEY_FOLDER)/extd.key#g;s#__USER_HOME_DIR__#$(EXTD_USER_HOME)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd_manager.py > $(BIN_DIR)/extd_manager

	sed 's#__PRIVATE_SSH_KEY__#$(USER_KEY_FOLDER)/extd#g;s#__PRIVATE_KEY__#$(USER_KEY_FOLDER)/extd.key#g;s#__USER_HOME_DIR__#$(EXTD_USER_HOME)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' extd_daemon.py > $(BIN_DIR)/extd_daemon

	sed 's#__PRIVATE_KEY__#$(EXTD_USER_HOME)/.ssh/extd.key#g;s#__USER_HOME_DIR__#$(EXTD_USER_HOME)#g;s#__DAEMON_PORT__#$(DAEMON_PORT)#g;s#__LISTENER_PORT__#$(LISTENER_PORT)#g' ssh_handler.py > $(EXTD_USER_HOME)/bin/ssh_handler

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
	rm -f $(BIN_DIR)/key_utils.py || echo "already clean"
	rm -f $(USER_KEY_FOLDER)/extd.key
	
	rm -rf $(EXTD_USER_HOME) || echo "already clean"
	rm -f $(USER_KEY_FOLDER)/extd.pub $(USER_KEY_FOLDER)/extd

	usermod -G "" extd || echo "no use or group"
	userdel -f -r extd || echo "no user"
	groupdel extd || echo "no group"

.PHONY: clean uninstall install
