#ifndef UTILS_C
#define UTILS_C

#include <errno.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <syslog.h>
#include <unistd.h>

#include "config.h"

struct msg_buff {
  long mtype;
  char *mtext;
};

void log_info(char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  syslog(LOG_INFO, fmt, args);
  va_end(args);
}

void log_error(char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  syslog(LOG_ERR, fmt, args);
  va_end(args);
}

int die(char *s) {
  // log_error(s, strerror(errno));
  log_error("%s", s);
  syslog(LOG_ERR, "%s: %s", s, strerror(errno));
  perror(s);
  exit(EXIT_FAILURE);
  return EXIT_FAILURE;
}

static pid_t daemonize() {
  pid_t pid;

  /* Fork off the parent process */
  pid = fork();

  /* An error occurred */
  if (pid < 0) exit(EXIT_FAILURE);

  /* Success: Let the parent terminate */
  if (pid > 0) exit(EXIT_SUCCESS);

  /* On success: The child process becomes session leader */
  if (setsid() < 0) exit(EXIT_FAILURE);

  /* Catch, ignore and handle signals */
  // TODO: Implement a working signal handler */
  signal(SIGCHLD, SIG_IGN);
  signal(SIGHUP, SIG_IGN);

  /* Fork off for the second time*/
  pid = fork();

  /* An error occurred */
  if (pid < 0) exit(EXIT_FAILURE);

  /* Success: Let the parent terminate */
  if (pid > 0) return pid;

  /* Set new file permissions */
  umask(0);

  /* Change the working directory to the root directory */
  /* or another appropriated directory */
  chdir(WORKING_DIR);

  /* Close all open file descriptors */
  int x;
  for (x = sysconf(_SC_OPEN_MAX); x >= 0; x--) {
    close(x);
  }

  /* Open the log file */
  openlog("extd", LOG_PID, LOG_DAEMON);

  return 0;
}

int get_queue() {
  int msqid;
  key_t key;

  if ((key = ftok(WORKING_DIR, 'A')) == -1) {
    die("ftok");
    exit(1);
  }

  if ((msqid = msgget(key, 0644 | IPC_CREAT)) == -1) {
    die("msgget");
    exit(1);
  }

  return msqid;
}

int send(int msqid, struct msg_buff *buf) {
  buf->mtype = 1; /* we don't really care in this case */
  int len = strlen(buf->mtext);
  return msgsnd(msqid, buf, len + 1, 0);
}

int receive(int msqid, struct msg_buff *buf) {
  return msgrcv(msqid, buf, sizeof buf->mtext, 0, 0);
}

#endif