.POSIX:

include config.mk

daemon: daemon.o
	$(CC) daemon.o -o daemon
daemon.o: daemon.c
	$(CC) -c daemon.c

listener: listener.o
	$(CC) listener.o -o listener
listener.o: listener.c
	$(CC) -c listener.c

ssh_handler: ssh_handler.o
	$(CC) ssh_handler.o -o ssh_handler
ssh_handler.o: ssh_handler.c
	$(CC) -c ssh_handler.c

extd: daemon ssh_handler listener

clean:
	rm -f *.o *.gch daemon ssh_handler listener *.out
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
	cp -f daemon $(DESTDIR)$(PREFIX)/bin
	cp -f listener $(DESTDIR)$(PREFIX)/bin
	cp -f ssh_handler $(DESTDIR)$(PREFIX)/bin
	sed 's#__BASE_DIR__#$(DESTDIR)$(PREFIX)#g' cli.sh > /usr/bin/extd_manager

	chown -R extd:extd $(DESTDIR)$(PREFIX)
	chown -R extd:extd /usr/bin/extd_manager

	chmod 6755 $(DESTDIR)$(PREFIX)/bin/listener
	chmod 6755 $(DESTDIR)$(PREFIX)/bin/daemon
	chmod 6755 $(DESTDIR)$(PREFIX)/bin/ssh_handler
	chmod 6755 /usr/bin/extd_manager

	usermod -s $(DESTDIR)$(PREFIX)/bin/ssh_handler extd

uninstall:
	rm -f /usr/bin/extd_manager
	rm -f $(DESTDIR)$(PREFIX)/bin/daemon
	rm -f $(DESTDIR)$(PREFIX)/bin/listener
	rm -f $(DESTDIR)$(PREFIX)/bin/ssh_handler
	rm -rf $(DESTDIR)$(PREFIX)/.ssh

	userdel -f -r extd || echo "no user"

.PHONY: clean install uninstall
