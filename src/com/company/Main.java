package com.company;

import java.sql.*;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Connection connection = null;
        String userTask = "";

        do {
            try {
                String url = "jdbc:mysql://localhost:3306/cinema_meiningen?user=root";
                connection = DriverManager.getConnection(url);
                String query = "select * from movies";

                try (Statement statementRead = connection.createStatement()) {
                    ResultSet resultSet = statementRead.executeQuery(query);
                    System.out.println("Cinema Meiningen, available movie's:");
                    // printing movie list
                    while (resultSet.next()) {
                        String movieName = resultSet.getString("movie_name");
                        int movieId = resultSet.getInt("movie_id");
                        System.out.println(movieId + " " + movieName);
                    }
                    // choosing movie by movieId number
                    // choosing number of seats
                    // searching for movie theater where chosen movie and seats are available
                    // if available seats found, booking will be processed
                    findAndOrderMovie(connection, chooseMovie(), chooseNumberOfSeats());
                    Scanner user = new Scanner(System.in);
                    System.out.println("do you wanna cancel an order, place next order or exit\ntype in:  cancel - place - exit");
                    userTask = user.nextLine();
                    if (userTask.equalsIgnoreCase("cancel")) {
                        // cancelling order by order number, seats get booked back
                        cancelOrder(connection);
                        Scanner user1 = new Scanner(System.in);
                        System.out.println("order canceled, place next order or exit\ntype in: place - exit");
                        userTask = user1.nextLine();
                    }
                } catch (SQLException e) {
                    System.out.println("read problem");
                }
            } catch (SQLException e) {
                throw new Error("connection problem", e);
            } finally {
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (SQLException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        } while (!userTask.equalsIgnoreCase("exit"));
    }


    public static int chooseMovie() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("choose movie by typing in the movie number");
        return scanner.nextInt();
    }

    public static int chooseNumberOfSeats() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("choose number of seats");
        return scanner.nextInt();
    }

    public static void findAndOrderMovie(Connection connection, int movienumber, int seats) {
        int availableTheaterNumber = 0;
        int theater = 1;
        do {
            int orderNumber;

            // searching for movie theater where movie with asked seats is available
            String query = "SELECT * FROM movie_theater_" + theater + " WHERE seats_left >= " + seats +
                    " AND movie_id =" + movienumber + ";";
            try (Statement statementRead = connection.createStatement()) {
                ResultSet resultSet = statementRead.executeQuery(query);
                if (resultSet.next()) {
                    availableTheaterNumber = theater;
                    System.out.println("movie: " + movienumber + " found in movie theater: " + theater + "\nbooking: yes - no");
                    Scanner scanner = new Scanner(System.in);

                    // if theater is found, movie theater seats will be updated
                    if (scanner.nextLine().equalsIgnoreCase("yes")) {
                        String update = "UPDATE movie_theater_" + theater + " SET seats_left = seats_left - " + seats +
                                " WHERE booking_id = " + resultSet.getInt("booking_id") + ";";
                        try (PreparedStatement preparedStatementUpdate = connection.prepareStatement(update)) {
                            preparedStatementUpdate.executeUpdate();
                        } catch (SQLException ex) {
                            System.out.println("movie theater update problem");
                            ex.printStackTrace();
                        }

                        // creating new order row with booking data
                        final String SQL_INSERT = "INSERT INTO orders (movie_theater, movie_booking_id, movie_id, seats)" +
                                "Values (?,?,?,?)";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
                            preparedStatement.setInt(1, theater);
                            preparedStatement.setInt(2, resultSet.getInt("booking_id"));
                            preparedStatement.setInt(3, resultSet.getInt("movie_id"));
                            preparedStatement.setInt(4, seats);
                            preparedStatement.executeUpdate();
                        } catch (SQLException ex) {
                            System.out.println("order update problem");
                            ex.printStackTrace();
                        }

                        // read out of order number created by mysql
                        String queryOrderNumber = "SELECT max(order_id) FROM orders;";
                        try (Statement statementOrderNumber = connection.createStatement()) {
                            ResultSet resultSetNumber = statementOrderNumber.executeQuery(queryOrderNumber);
                            resultSetNumber.next();
                            orderNumber = resultSetNumber.getInt("max(order_id)");
                            System.out.println("order complete, your order number is: " + orderNumber);
                        } catch (SQLException ex) {
                            System.out.println("order update problem");
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println("theater searching problem");
            }
            theater++;
        } while (availableTheaterNumber == 0 && theater <= 5);
        if (theater > 5) System.out.println("for movie: " + movienumber + " no seats left");
    }

    public static void cancelOrder(Connection connection) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("type in order number to be canceled:");
        int orderId = scanner.nextInt();

        // checking if order number is registered, if registered, data of order will be read out
        String query = "SELECT * FROM orders WHERE order_id = " + orderId + ";";
        try (Statement statementOrder = connection.createStatement()) {
            ResultSet resultSet = statementOrder.executeQuery(query);
            if (resultSet.next()) {
                int movieTheater = resultSet.getInt("movie_theater");
                int bookingId = resultSet.getInt("movie_booking_id");
                int bookedSeats = resultSet.getInt("seats");

                // in movie theater in which movie got booked, seats will be set back to status before order
                String update = "UPDATE movie_theater_" + movieTheater + " SET seats_left = seats_left + " + bookedSeats +
                        " WHERE booking_id = " + bookingId + ";";
                try (PreparedStatement preparedStatementUpdate = connection.prepareStatement(update)) {
                    preparedStatementUpdate.executeUpdate();
                } catch (SQLException ex) {
                    System.out.println("movie theater canceling update problem");
                    ex.printStackTrace();
                }
            } else {
                System.out.println("wrong order number");
            }
        } catch (SQLException e) {
            System.out.println("can't cancel order");
        }

        // for the cancel order, cancelled is set to true in database
        String update1 = "UPDATE orders " + " SET cancelled = true WHERE order_id = " + orderId + ";";
        try (PreparedStatement preparedStatementUpdate = connection.prepareStatement(update1)) {
            preparedStatementUpdate.executeUpdate();
        } catch (SQLException ex) {
            System.out.println("movie theater canceling update problem");
            ex.printStackTrace();
        }
    }
}
