package io.fabric8.kubernetes.workflow;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class PodExecDecorator extends LauncherDecorator implements Serializable {

    private final transient KubernetesFacade kubernetes;
    private final transient String name;
    private final transient AtomicBoolean alive;
    private final transient CountDownLatch started;
    private final transient CountDownLatch finished;

    public PodExecDecorator(KubernetesFacade kubernetes, String name, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this.kubernetes = kubernetes;
        this.name = name;
        this.alive = alive;
        this.started = started;
        this.finished = finished;
    }

    @Override
    public Launcher decorate(final Launcher launcher, Node node) {
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(Launcher.ProcStarter starter) throws IOException {
                AtomicBoolean processAlive = new AtomicBoolean(false);
                CountDownLatch processStarted = new CountDownLatch(1);
                CountDownLatch processFinished = new CountDownLatch(1);

                ExecWatch execWatch = kubernetes.exec(name, processAlive, processStarted, processFinished, launcher.getListener().getLogger(),
                        getCommands(starter)
                );
                return new PodExecProc(name, processAlive, processFinished, execWatch);
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                kubernetes.deletePod(name);
            }
        };
    }

    static String[] getCommands(Launcher.ProcStarter starter) {
        List<String> allCommands = new ArrayList<String>();

        boolean first = true;
        for (String cmd : starter.cmds()) {
            if (first && "nohup".equals(cmd)) {
                first = false;
                continue;
            }
            //I shouldn't been doing that, but clearly the script that is passed to us is wrong?
            allCommands.add(cmd.replaceAll("\\$\\$", "\\$"));
        }
        return allCommands.toArray(new String[allCommands.size()]);
    }
}
