package com.aubg.seniorproject;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.sql.*;
import java.util.*;


import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;

import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SerialTest implements SerialPortEventListener {
    SerialPort serialPort;
    /**
     * The port we're normally going to use.
     */
    private static final String PORT_NAMES[] = {
            "/dev/tty.usbserial-A9007UX1", // Mac OS X
            "/dev/ttyACM0", // Raspberry Pi
            "/dev/ttyUSB0", // Linux
            "COM4", // Windows
    };

    private static final Map<Integer, String> database = new HashMap<>();


    /**
     * Fields used for encryption/decryption of passwords
     **/
    private static final String ALGO = "AES";
    private static final byte[] keyValue =
            new byte[]{'T', 'h', 'e', 'B', 'e', 's', 't', 'S', 'e', 'c', 'r', 'e', 't', 'K', 'e', 'y'};


    private Connection connect = null;
    private Statement statement = null;
    private PreparedStatement preparedStatement = null;
    private ResultSet resultSet = null;
    ChromeDriver driver = null;


    /**
     * A BufferedReader which will be fed by a InputStreamReader
     * converting the bytes into characters
     * making the displayed results codepage independent
     */
    private BufferedReader input;
    /**
     * The output stream to the port
     */
    private OutputStream output;
    /**
     * Milliseconds to block while waiting for port open
     */
    private static final int TIME_OUT = 2000;
    /**
     * Default bits per second for COM port.
     */
    private static final int DATA_RATE = 9600;

    public void initialize() {
        // the next line is for Raspberry Pi and
        // gets us into the while loop and was suggested here was suggested http://www.raspberrypi.org/phpBB3/viewtopic.php?f=81&t=32186
        //System.setProperty("gnu.io.rxtx.SerialPorts", "/dev/ttyACM0");
        System.setProperty("webdriver.chrome.driver", "chromedriver.exe");


        // This will load the MySQL driver, each DB has its own driver
        try {
            Class.forName("com.mysql.jdbc.Driver");

            // Setup the connection with the DB
            connect = DriverManager
                    .getConnection("jdbc:mysql://localhost:3306/fingerprint_login", "aubgstudent", "aubgstudent");

            // Statements allow to issue SQL queries to the database
            statement = connect.createStatement();


        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }


        CommPortIdentifier portId = null;
        Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();


        //First, Find an instance of serial port as set in PORT_NAMES.
        while (portEnum.hasMoreElements()) {
            CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
            for (String portName : PORT_NAMES) {
                if (currPortId.getName().equals(portName)) {
                    portId = currPortId;
                    break;
                }
            }
        }
        if (portId == null) {
            System.out.println("Could not find COM port.");
            return;
        }

        try {
            // open serial port, and use class name for the appName.
            serialPort = (SerialPort) portId.open(this.getClass().getName(),
                    TIME_OUT);

            // set port parameters
            serialPort.setSerialPortParams(DATA_RATE,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            // open the streams
            input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
            output = serialPort.getOutputStream();

            // add event listeners
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }

    /**
     * This should be called when you stop using the port.
     * This will prevent port locking on platforms like Linux.
     */
    public synchronized void close() {
        if (serialPort != null) {
            serialPort.removeEventListener();
            serialPort.close();
        }
    }

    /**
     * Handle an event on the serial port. Read the data and print it.
     */
    public synchronized void serialEvent(SerialPortEvent oEvent) {
        if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
                String inputLine = input.readLine();
                System.out.println(inputLine);


                if (isNumeric(inputLine)) {

                    // Setting up the connection to the webdriver
                    if (driver == null) {
                        driver = new ChromeDriver();
                    } else {
                        try {

                            driver.executeScript("window.open()");
                            ArrayList<String> tabs = new ArrayList<>(driver.getWindowHandles());
                            driver.switchTo().window(tabs.get(tabs.size()-1));


                        } catch (WebDriverException e) {
                            driver = new ChromeDriver();
                        }
                    }

                    System.out.println("SUCCESS");

                    //query the database (open connection to database in the init method)

                    // Result set get the result of the SQL query
                    resultSet = statement
                            .executeQuery("select * from user where id=" + Integer.parseInt(inputLine.trim()));

                    String username = "";
                    String socialNetwork = "";
                    String password = "";

                    if (resultSet.next()) {
                        // get the username and social network
                        username = resultSet.getString("username");
                        socialNetwork = resultSet.getString("social_network");

                        // get the password hash and decode it
                        String passwordHash = resultSet.getString("password_hash");
                        password = decrypt(passwordHash);
                    }


                    switch (socialNetwork) {
                        case "Facebook":
                            driver.get("https://www.facebook.com");
                            // feed this to the driver by using selenium
                            driver.findElement(By.id("email")).sendKeys(username);
                            driver.findElement(By.id("pass")).sendKeys(password);
                            driver.findElement(By.id("u_0_2")).click();
                            break;
                        case "Instagram":
                            driver.get("https://www.instagram.com");
                            // feed this to the driver by using selenium
                            driver.findElement(By.xpath("//a[contains(text(),'Log in')]")).click();
                            driver.findElement(By.xpath("//input[@placeholder='Phone number, username, or email']")).sendKeys(username);
                            driver.findElement(By.xpath("//input[@placeholder='Password']")).sendKeys(password);
                            driver.findElement(By.xpath("//button[contains(text(),'Log in')]")).click();
                            break;
                        case "Twitter":
                            driver.get("https://twitter.com");
                            // feed this to the driver by using selenium
                            driver.findElement(By.id("signin-email")).sendKeys(username);
                            driver.findElement(By.id("signin-password")).sendKeys(password);
                            driver.findElement(By.xpath("//button[contains(text(),'Log in')]")).click();
                            break;
                        default:
                            break;

                    }


                }


            } catch (Exception e) {
                System.err.println(e.toString());
            }
        }
        // Ignore all the other eventTypes.
    }


    public static boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }


    /**
     * Decrypt a string with AES algorithm.
     *
     * @param encryptedData is a string
     * @return the decrypted string
     */
    public static String decrypt(String encryptedData) throws Exception {
        Key key = generateKey();
        Cipher c = Cipher.getInstance(ALGO);
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decordedValue = Base64.getDecoder().decode(encryptedData);
        byte[] decValue = c.doFinal(decordedValue);
        return new String(decValue);
    }

    /**
     * Generate a new encryption key.
     */
    private static Key generateKey() throws Exception {
        return new SecretKeySpec(keyValue, ALGO);
    }


    public static void main(String[] args) throws Exception {
        SerialTest main = new SerialTest();
        main.initialize();
        Thread t = new Thread() {
            public void run() {
                //the following line will keep this app alive for 1000 seconds,
                //waiting for events to occur and responding to them (printing incoming messages to console).
                try {
                    Thread.sleep(1000000);
                } catch (InterruptedException ie) {
                }
            }
        };
        t.start();
        System.out.println("Started");


    }
}