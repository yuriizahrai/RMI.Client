package rmi;

import command_features.Command;
import lombok.Data;
import lpi.server.rmi.Compute;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class RemoteHendlerRMI implements Closeable {

    private Registry registry;
    private Compute proxy;
    private int port;
    private String hostname;
    private ProtocolManager protocolManager = new ProtocolManager();
    private String sessionId;
    private String[] listUsers;
    private volatile String responseInfo;
    private boolean isRegex = false;
    private long timeToMesure;

    private TimerTask timerTaskReciveChecker;
    private Timer timerReciveChecker;

    public RemoteHendlerRMI(String hostname, int port) {
        this.port = port;
        this.hostname = hostname;
    }

    public boolean registClient() {
        try {
            this.registry = LocateRegistry.getRegistry(hostname, port);
            this.proxy = (Compute) registry.lookup(Compute.RMI_SERVER_NAME);
            resiveInfoChecker();
            return true;
        } catch (NotBoundException | RemoteException e) {
            close();
            return false;
        }
    }

    @Override
    public void close() {
        if (this.proxy == null) {
            this.registry = null;
            this.proxy = null;
        } else {
            timerTaskReciveChecker.cancel();
            timerReciveChecker.cancel();
            this.registry = null;
            this.proxy = null;
        }
    }

    public void commandExecute(String textFromLabel) {
        commandCheker(protocolManager.textParser(textFromLabel));
    }

    private void commandCheker(String textFromLable) {
        Matcher matcher;
        for (final Command command : Command.values()) {
            matcher = Pattern.compile(command.getRegex()).matcher(textFromLable);
            if (matcher.find()) {
                try {
                    switch (command) {
                        case CMD_PING:
                            proxy.ping();
                            isRegex = true;
                            break;
                        case CMD_ECHO:
                            responseInfo = new String(proxy.echo(new String(protocolManager.parserCommandToGetSinglePostArgument(textFromLable))));
                            isRegex = true;
                            break;
                        case CMD_PROCESS:
                            String[] itemForProcces = protocolManager.parserCommandToGetPluralPostArgument(textFromLable);
                            process(itemForProcces[1], itemForProcces[2]);
                            isRegex = true;
                            break;
                        case CMD_GEN:
                            String[] itemForGenerating = protocolManager.parserCommandToGetPluralPostArgument(textFromLable);
                            generatingRundomSeqence(itemForGenerating[1], itemForGenerating[2]);
                            isRegex = true;
                            break;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!isRegex) {
            responseInfo = new String("Неправильно введена команда");
        }
        isRegex = false;
    }

    private void process(String stringPathRandFile, String stringPathSortFile) {

        Path pathToRandomNumFile = Paths.get(stringPathRandFile);
        File fileRandomNum = new File(pathToRandomNumFile.toString());

        Path pathToSortNumFile = Paths.get(stringPathSortFile);
        File fileSortNum = new File(pathToSortNumFile.toString());

        Compute.BinaryTree binaryTree = null;
        try {
            binaryTree = new Compute.BinaryTree(fileSortNum.getName(), fileRandomNum);
        } catch (NoSuchFileException e) {
            responseInfo = new String("Такого файлу не інує, перевірте ввід та спробуйте ще раз!");
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        byte[] bytes = new byte[0];
        try {
            bytes = proxy.executeTask(binaryTree);
        } catch (RemoteException e) {
            responseInfo = new String("Було виявлено помилку опрацювання серверу");
        }
        try {
            if (fileSortNum.exists()) {
                fileSortNum.delete();
                fileSortNum.createNewFile();
            }
            Files.write(pathToSortNumFile, bytes, StandardOpenOption.APPEND);
        } catch (NoSuchFileException e) {
            responseInfo = new String("Такого файлу не інує, перевірте ввід та спробуйте ще раз!");
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            long timeProcesMethod = proxy.timeProcesMethod(binaryTree);
            responseInfo = new String("Відсортовано алгоритмом бінарного дерева = " + timeProcesMethod / 1000000 + " msec.");
        } catch (RemoteException e) {
            responseInfo = new String("Було виявлено помилку опрацювання серверу");
        }
    }

    private void generatingRundomSeqence(String pathToDirect, String fileName) {

            int[] arrayRandNum = new int[1000000];
            boolean isRecreate = false;
            Random generator = new Random();
            StringBuilder finalGenRandSeq = new StringBuilder();
            for (int i = 0; i < arrayRandNum.length; i++) {
                if (i != arrayRandNum.length - 1) {
                    arrayRandNum[i] = generator.nextInt(1000000000);
                    finalGenRandSeq.append(arrayRandNum[i] + " ");
                } else {
                    arrayRandNum[i] = generator.nextInt(1000000000);
                    finalGenRandSeq.append(arrayRandNum[i]);
                }
            }

        Path pathToDir = Paths.get(pathToDirect, fileName);
        System.out.println(pathToDir.toString());
        File fileRandNum = new File(pathToDir.toString());

        if (fileRandNum.exists()) {
            fileRandNum.delete();
            isRecreate = true;
        }

        try {
            fileRandNum.createNewFile();
            if (!isRecreate) {
                responseInfo = new String("File - " + pathToDir.toString() + " створенний з рандомними числами!");
            } else {
                responseInfo = new String("File - " + pathToDir.toString() + " перестворенний з рандомними числами ");
            }
        } catch (IOException e) {
            responseInfo = new String("Шлях" + pathToDir.toString() + " Неіснує");
            return;
        }
        try {
            Files.write(pathToDir, finalGenRandSeq.toString().getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
            responseInfo = new String("Проблеми з записом до файлу");
            return;
        }
    }

    private synchronized void resiveInfoChecker() {

        timerTaskReciveChecker = new TimerTask() {
            @Override
            public void run() {
                try {
                    proxy.ping();
                } catch (RemoteException e) {
//                    e.printStackTrace();
                    responseInfo = new String("Сервер вилетів");
                    close();
                }
            }
        };
        timerReciveChecker = new Timer("TimerReciveChecker");
        timerReciveChecker.scheduleAtFixedRate(timerTaskReciveChecker, 500, 500);
    }

}
