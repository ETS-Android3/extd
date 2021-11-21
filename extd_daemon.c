#include <errno.h>
#include <signal.h>
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
#include "util.c"

#define PID_FILE_LOCATION WORKING_DIR "/daemon.pid"

volatile sig_atomic_t stop;

void sig_handler(int signum) {
  if (signum == SIGINT) {
    stop = 1;
  }
}

int main() {
  // pid_t pid = daemonize();

  // if (pid > 0) {
  //   FILE *fp;
  //   char *file = PID_FILE_LOCATION;

  //   if ((fp = fopen(file, "w")) == NULL) {
  //     die("pid_file");
  //   }

  //   if (fprintf(fp, "%llu", pid) < 0) {
  //     die("fprintf");
  //   }

  //   fclose(fp);
  //   return EXIT_SUCCESS;
  // }

  if (signal(SIGINT, sig_handler) == SIG_ERR) die("signal");
  log_info("Started ExtD server\n");

  struct msg_buff buf;
  int msqid = get_queue();

  log_info("ready to receive messages.\n");

  while (!stop) {
    if (receive_m(msqid, &buf) == -1) {
      if (errno == EINTR) break;
      die("msgrcv");
      exit(1);
    }

    // if (strcmp(buf.mtext, "quit") == 0) break;
    log_info("%s\n", buf.mtext);

    // char **split = str_split(buf.mtext, ':');
    // int size = (sizeof split / sizeof *split) + 1;

    // if (size == 4) {
    //   if (strcmp(split[0], "con") == 0) {
    //     log_info("dimensions: %sx%s\n", split[1], split[2]);
    //     // int pid = fork();

    //     // if (0 == pid) {
    //     //   char logfile[50];
    //     //   sprintf(logfile, "/usr/share/extd/%s.log", split[1]);

    //     //   char *const args[10] = {
    //     //       "/usr/bin/x11vnc", "-once", "-timeout", "30",
    //     //       "--localhost",     "-o",    logfile,    "-threads",
    //     //       "-passwd",         split[3]};

    //     //   execvp("/usr/bin/x11vnc", args);
    //     // }
    //   } else {
    //     log_error("invalid command: %s.", split[0]);
    //   }
    // } else {
    //   log_error(
    //       "invalid parameters received. expected a list of 3 "
    //       "arguments separated by \":\". got: \"%s\"\n",
    //       buf.mtext);
    // }
  }

  if (msgctl(msqid, IPC_RMID, NULL) != 0) {
    die("msgctl");
    exit(1);
  }

  // if (remove(PID_FILE_LOCATION) != 0) {
  //   die("pid_file");
  // }

  log_info("Stopped ExtD server\n");
  closelog();

  return EXIT_SUCCESS;
}