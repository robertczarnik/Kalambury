package server;
// Java implementation of  Server side
// It contains two classes : Server and ClientHandler

import collections.Pair;
import objects.ColorRGB;
import objects.Guess;
import objects.Message;
import objects.Point;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

// Server class
public class Server {

    private static Set<Pair<ObjectOutputStream,String>> clients = new LinkedHashSet<>(); //set z kolejnoscia w jakies zostaly dodawane elementy
    private static String secretWord = "okon";
    private static int queueNumber=1;
    private static int time=10;
    private static List<Pair<String,Integer>> scoreboard = Collections.synchronizedList(new ArrayList<>());



    public static void main(String[] args) throws IOException {
        System.out.println("The chat server is running...");

        var pool = Executors.newFixedThreadPool(4);

        try (var listener = new ServerSocket(5001)) {
            while (true) {
                pool.execute(new ClientHandler(listener.accept()));

            }
        }
    }


    // ClientHandler class
    static class ClientHandler implements Runnable {
        ObjectInputStream in;
        ObjectOutputStream out;
        final Socket socket;
        String response;
        String msgType;
        String msg;
        String name;
        Guess msgResponse;


        // Constructor
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        //TODO: dopiescic
        private String checkGuess( String guess){
            if(guess.equals(secretWord))
                return "OK";
            else {
                return guess;
            }

        }

        private void broadcast(Object obj,ObjectOutputStream out) throws IOException {
            for (var client : clients) {
                if (client.getFirst() != out) {
                    client.getFirst().writeObject(obj);
                    client.getFirst().flush();
                }
            }
        }

        private void broadcastAll(Object obj) throws IOException {
            for (var client : clients) {
                client.getFirst().writeObject(obj);
                client.getFirst().flush();
                client.getFirst().reset(); //reset cache bo zaszala zmiana w liscie scoreboard
            }
        }

        private void changeDrawer() throws IOException {
            Iterator<Pair<ObjectOutputStream,String>> iterator = clients.iterator();

            if(queueNumber>clients.size()) queueNumber=1;

            int i=1;
            while(iterator.hasNext()) {
                Pair<ObjectOutputStream,String> setElement = iterator.next();
                if(i==queueNumber) {
                    ObjectOutputStream output = setElement.getFirst();
                    broadcast(new Message("DRAWER","false,"+time),output);
                    output.writeObject(new Message("DRAWER","true,"+time));
                    output.flush();
                    queueNumber++;
                    break;
                }
                i++;
            }

        }

        private void removePlayer(){
            if (out != null) {
                Iterator<Pair<ObjectOutputStream,String>> iterator = clients.iterator();

                while(iterator.hasNext()) {
                    Pair<ObjectOutputStream,String> setElement = iterator.next();
                    if(setElement.getFirst()==out) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        @Override
        public void run() {
            try {
                System.out.println("nowy client!");
                this.out = new ObjectOutputStream(socket.getOutputStream());
                this.in = new ObjectInputStream(socket.getInputStream());

                if(clients.isEmpty()){ //ustanowienie 1 klienta jako admina stolika
                    clients.add(new Pair<>(out,""));
                    out.writeObject(new Message("ADMIN","true"));
                }else{
                    clients.add(new Pair<>(out,""));
                }


                while (true) {
                    Object input = in.readObject();

                    if(input instanceof Guess){
                        String guess = ((Guess)input).getGuess();
                        response = checkGuess(guess);
                        msgResponse = new Guess(name+ ": " + guess); // guess with player name
                        broadcast(msgResponse,out);

                        if(response.equals("OK")) {
                            //punktyy
                            changeDrawer();
                            broadcastAll(new Guess("BRAWO! Haslo to: " + guess));
                        }
                    }
                    else if(input instanceof Point || input instanceof ColorRGB) {
                        broadcast(input,out);
                    }else if(input instanceof Message){
                        msgType=((Message)input).getMessageType();
                        msg=((Message)input).getMessage();

                        if(msgType.equals("NAME")){
                            scoreboard .add(new Pair<>(msg,0)); // synchronized call

                            Iterator<Pair<ObjectOutputStream,String>> iterator = clients.iterator();

                            while(iterator.hasNext()) {
                                Pair<ObjectOutputStream,String> setElement = iterator.next();
                                if(setElement.getFirst()==out) {
                                    setElement.setSecond(msg);
                                    name=msg;
                                    break;
                                }
                            }

                            Iterator<Pair<String,Integer>> iterators = scoreboard .iterator();

                            while(iterators.hasNext()){
                                Pair<String,Integer> ele = iterators.next();
                                System.out.println(ele.getFirst());
                            }

                            broadcastAll(scoreboard);


                        }else if(msgType.equals("START")){ //game start
                            changeDrawer();
                        }else if(msgType.equals("EXIT")){
                            break;
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("BYE BYE ");
            }finally {
                removePlayer();

                try {
                    socket.close();
                }
                catch (IOException e) { }
            }

        }
    }
}