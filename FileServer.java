import java.io.*;                  // IOException
import java.net.*;                 // InetAddress
import java.rmi.*;                 // Naming
import java.rmi.server.*;          // UnicastRemoteObject
import java.rmi.registry.*;        // rmiregistry
import java.util.ArrayList;


public class FileServer extends UnicastRemoteObject
        implements ServerInterface {

    private static final String rootPath = "/tmp/";
    private static int port;
    private static ArrayList<FileCacheEntry> fileCacheList;

    protected FileServer() throws RemoteException {
        fileCacheList = new ArrayList<>();

    }


    public FileContents download(String client, String filename, String mode) throws RemoteException {
        //scans the list of file cache entries
        boolean isInList = false;
        FileCacheEntry entry = null;
        for (FileCacheEntry fileCacheEntry : fileCacheList) {
            if (fileCacheEntry.getName().equals(filename)) {
                entry = fileCacheEntry;
                isInList = true;
                break;
            }
        }

        if (!isInList) {

            //if the server does not find a requested file’s cache entry in this list
            try {

                System.out.println("Download request by " + client + ". Mode: " + (mode.equals("r") ? "read" : "write"));
                //reads the file contents into the entry from its working directory
                String filePath = rootPath + filename;
                FileInputStream file = null;
                file = new FileInputStream(filePath);
                byte[] bytes = new byte[file.available()];
                file.read(bytes);
                //create and add the entry to the list
                entry = new FileCacheEntry(filename);
                entry.setBytes(bytes);
                if (mode.equals("r")) {
                    //register client's IP name in the readers of this cache
                    entry.addReaders(client);
                    //changes the entry state from “Not_Shared” to “Read_Shared”
                    entry.setState_readshared();
                    System.out.println("File: " + filename + "FileSize: " + bytes.length + "bytes ServerFileState: read_shared");
                    System.out.println("#readers: 1 owner: none");
                } else {
                    //register client's IP name in the owner of this cache
                    entry.setOwner(client);
                    //entry state changes from “Not_Shared” to “Write_Shared"
                    entry.setState_writeshared();
                    System.out.println("File: " + filename + "FileSize: " + bytes.length + "bytes ServerFileState: write_shared");
                    System.out.println("#readers: 0 owner: " + client);
                }

                fileCacheList.add(entry);
                //store the cached file contents in a FileContent object and passes the DFS client

                return new FileContents(bytes);

            } catch (IOException e) {
                //if file not found in disk
                if (mode.equals("w")) {
                    //create entry for the client if file not existed for write request
                    entry = new FileCacheEntry(filename);
                    // create file content as empty contents.length == 0
                    byte[] bytes = new byte[0];
                    entry.setBytes(bytes);
                    entry.setOwner(client);
                    entry.setState_writeshared();
                    fileCacheList.add(entry);
                    System.out.println("File: " + filename + "FileSize: " + bytes.length + "bytes ServerFileState: write_shared");
                    System.out.println("#readers: 0 owner: " + client);
                    //store the cached file contents in a FileContent object and passes the DFS client
                    FileContents fileContents = new FileContents(bytes);
                    return fileContents;
                } else {
                    System.out.println("File " + filename + " not found in the list and disk");
                    //returns "null" to the client if file not existed for read request
                    return null;
                }
            }

        } else {
            //if the server find the request file in the list
            return entry.download(client, mode);
        }

    }


    public boolean upload(String client, String filename, FileContents contents) {
        //find file entry in cache
        FileCacheEntry entry = null;
        for (FileCacheEntry fileCacheEntry : fileCacheList) {
            if (fileCacheEntry.getName().equals(filename)) {
                entry = fileCacheEntry;
            }
        }
        if (entry != null) {
            return entry.upload(client, contents);
        } else {
            return false;
        }
        //resuming the download( ) function that has tried to download the same file for a write
    }

    private static void startRegistry(int port) throws RemoteException {
        try {
            Registry registry =
                    LocateRegistry.getRegistry(port);
            registry.list();
        } catch (RemoteException e) {
            Registry registry =
                    LocateRegistry.createRegistry(port);
        }
    }


    private void loop() {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                //terminate server
                String inputCMD = input.readLine();
                if (inputCMD.toLowerCase().equals("quit") || inputCMD.toLowerCase().equals("exit")) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        writeCacheToDisk();
        System.exit(1);
    }

    public static void main(String args[]) {
        if (args.length != 1) {
            System.exit(-1);
        }
        port = Integer.parseInt(args[0]);
        //name bind
        try {
            //RMI registry
            startRegistry(port);
            FileServer fileServer = new FileServer();
            Naming.rebind("rmi://localhost:" + port + "/fileserver", fileServer);
            System.out.println("rmi://localhost: " + port + "/fileserver" +
                    " invokded");
            fileServer.loop();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

    }

    private void writeCacheToDisk() {
        for (FileCacheEntry fileCacheEntry : fileCacheList
        ) {
            try {
                String filePath = rootPath + fileCacheEntry.getName();
                FileOutputStream outputStream = new FileOutputStream(filePath);
                outputStream.write(fileCacheEntry.getBytes());
                outputStream.close();
                System.out.println("File " + fileCacheEntry.getName() + " write to disk");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class FileCacheEntry {
        private final static int state_notshared = 0;
        private final static int state_readshared = 1;
        private final static int state_writeshared = 2;
        private final static int state_ownershipchange = 3;
        private int state = state_notshared;
        private String name = "";
        private byte[] bytes = null;
        private ArrayList<String> readers = null;
        //private int suspendedDownloadInQueue = 0;
        private String owner = "";

        public FileCacheEntry() {
            readers = new ArrayList<>();
        }

        public FileCacheEntry(String name) {
            this.name = name;
            readers = new ArrayList<>();
        }

        public boolean isState_notshared() {
            return this.state == state_notshared;
        }

        public boolean isState_readshared() {
            return this.state == state_readshared;
        }

        public boolean isState_writeshared() {
            return this.state == state_writeshared;
        }

        public boolean isState_ownershipchange() {
            return this.state == state_ownershipchange;
        }


        public void setState_notshared() {
            this.state = state_notshared;
        }

        public void setState_readshared() {
            this.state = state_readshared;
        }

        public void setState_writeshared() {
            this.state = state_writeshared;
        }

        public void setState_ownershipchange() {
            this.state = state_ownershipchange;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public void setBytes(byte[] bytes) {
            this.bytes = bytes;
        }

        public ArrayList<String> getReaders() {
            return readers;
        }

        public void addReaders(String reader) {
            readers.add(reader);
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        private String stateNameRetrieve() {
            String stateName = "";
            switch (state) {
                case 0:
                    stateName = "not_shared";
                    break;
                case 1:
                    stateName = "read_shared";
                    break;
                case 2:
                    stateName = "write_shared";
                    break;
                case 3:
                    stateName = "ownership_changed";
                    break;
            }
            return stateName;
        }

        private synchronized boolean upload(String clientName, FileContents content) {
            try {
                //check file states is ownership_change or write_shared

                if (isState_writeshared()) {
                    //If the entry state is “Write_Shared”, the server changes the entry state to “Not_Shared”.
                    setState_notshared();

                } else if (isState_ownershipchange()) {
                    //If the entry state is “Ownership_Change”, the server changes the entry state to “Write_Shared”
                    setState_writeshared();
                } else {
                    return false;
                }

                //check ownership with client name
                if (owner != null && owner.equals(clientName)) {
                    //store file into entry
                    setBytes(content.get());
                } else {
                    return false;
                }
                String stateName = stateNameRetrieve();
                System.out.println("Upload request by " + clientName + ".");
                System.out.println("File: " + getName() + " FileSize: " + bytes.length + "bytes ServerFileState_AfterUpload: " + stateName);
                System.out.println("#readers: " + readers.size() + " owner: " + (owner != null ? owner : "none"));
                //request invalidate
                for (String reader : readers) {
                    ((ClientInterface) Naming.lookup("rmi://" + reader + ":" + port + "/fileclient")).invalidate();
                }
                //empty the list
                readers.clear();
                this.notify();
                return true;
            } catch (RemoteException | NotBoundException | MalformedURLException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void downloadServerPrint(String clientName, String mode) {
            String stateName = stateNameRetrieve();
            System.out.println("Download request by " + clientName + ". Mode: " + (mode.equals("r") ? "read" : "write"));
            System.out.println("File: " + getName() + "FileSize: " + bytes.length + "bytes ServerFileState: " + stateName);
            System.out.println("#readers: " + readers.size() + " owner: " + (owner != null ? owner : "none"));
        }

        private synchronized FileContents download(String clientName, String mode) {
            try {

                //download as read
                if (mode.equals("r")) {
                    if (isState_readshared() || isState_notshared()) {
                        //read file with read_shared/not_shared
                        readers.add(clientName);
                        setState_readshared();
                        //print server info on download
                        downloadServerPrint(clientName, mode);
                        return new FileContents(bytes);

                    } else if (isState_writeshared() || isState_ownershipchange()) {
                        //read file with Ownership_Change states/write_shared states
                        readers.add(clientName);
                        //print server info on download
                        downloadServerPrint(clientName, mode);
                        return new FileContents(bytes);
                    }
                } else {
                    //download as write
                    if (isState_readshared() || isState_notshared()) {
                        //write file with read_share states
                        owner = clientName;
                        setState_writeshared();
                        //print server info on download
                        downloadServerPrint(clientName, mode);
                        return new FileContents(bytes);

                    } else if (isState_writeshared()) {
                        //write file with write_share states
                        setState_ownershipchange();
                        //print server info on download
                        downloadServerPrint(clientName, mode);
                        //request writeback
                        ((ClientInterface) Naming.lookup("rmi://" + owner + ":" + port + "/fileclient")).writeback();
                        try {
                            System.out.println("Download suspend on " + clientName);
                            //wait for upload to notify
                            this.wait();
                            //notify the thread that wait on ownershipchagne states
                            this.notifyAll();
                            System.out.println("Download resume on " + clientName);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        owner = clientName;
                        return new FileContents(bytes);

                    } else if (isState_ownershipchange()) {
                        //write file with Ownership_Change states, resume until states change to write_share

                        try {
                            //print server info on download
                            downloadServerPrint(clientName, mode);
                            System.out.println("Download suspend on " + clientName);
                            //wait for download to notifyAll
                            this.wait();
                            //request writeback
                            ((ClientInterface) Naming.lookup("rmi://" + owner + ":" + port + "/fileclient")).writeback();
                            //wait for upload to notify
                            this.wait();
                            //notify the other threads that wait on ownershipchagne states
                            this.notifyAll();
                            System.out.println("Download resume on " + clientName);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        setState_writeshared();
                        owner = clientName;
                        return new FileContents(bytes);
                    } else {
                        return null;
                    }

                }


            } catch (RemoteException | NotBoundException | MalformedURLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
