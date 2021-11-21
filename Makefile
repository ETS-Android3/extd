.POSIX:

include config.mk

extd_daemon: extd_daemon.o
	$(CC) extd_daemon.o -o extd_daemon
extd_daemon.o: extd_daemon.c
	$(CC) -c extd_daemon.c

extd_listener: extd_listener.o
	$(CC) extd_listener.o -o extd_listener
extd_listener.o: extd_listener.c
	$(CC) -c extd_listener.c

ssh_handler: ssh_handler.o
	$(CC) ssh_handler.o -o ssh_handler
ssh_handler.o: ssh_handler.c
	$(CC) -c ssh_handler.c

extd: extd_daemon ssh_handler extd_listener

clean:
	rm -f *.o *.gch extd_daemon ssh_handler extd_listener *.out
install: extd
	useradd --system -d $(DESTDIR)$(PREFIX) -m extd
	echo 'extd:1234k' | chpasswd
	chmod 755 $(DESTDIR)$(PREFIX)

	find $(DESTDIR)$(PREFIX) -name ".*" -exec rm -rf {} +

	mkdir $(DESTDIR)$(PREFIX)/.ssh
	chmod 700 $(DESTDIR)$(PREFIX)/.ssh
	touch $(DESTDIR)$(PREFIX)/.ssh/authorized_keys
	chmod 600 $(DESTDIR)$(PREFIX)/.ssh/authorized_keys

	# ssh-keygen -t rsa -b 4096 -f $(DESTDIR)$(PREFIX)/.ssh/id_rsa -N ""
	# chmod 600 $(DESTDIR)$(PREFIX)/.ssh/id_rsa
	# chmod 644 $(DESTDIR)$(PREFIX)/.ssh/id_rsa.pub

	mkdir -p $(DESTDIR)$(PREFIX)/bin
	cp -f extd_daemon $(DESTDIR)$(PREFIX)/bin
	cp -f extd_listener $(DESTDIR)$(PREFIX)/bin
	cp -f ssh_handler $(DESTDIR)$(PREFIX)/bin
	sed 's#__BASE_DIR__#$(DESTDIR)$(PREFIX)#g' extd_manager.sh > /usr/bin/extd_manager

	chown -R extd:extd $(DESTDIR)$(PREFIX)
	chown -R extd:extd /usr/bin/extd_manager

	chmod 6755 $(DESTDIR)$(PREFIX)/bin/*
	chmod 6755 /usr/bin/extd_manager

	usermod -s $(DESTDIR)$(PREFIX)/bin/ssh_handler extd

uninstall:
	rm -f /usr/bin/extd_manager
	rm -f $(DESTDIR)$(PREFIX)/bin/extd_daemon
	rm -f $(DESTDIR)$(PREFIX)/bin/extd_listener
	rm -f $(DESTDIR)$(PREFIX)/bin/ssh_handler
	rm -rf $(DESTDIR)$(PREFIX)/.ssh

	userdel -f -r extd || echo "no user"

.PHONY: clean install uninstall
