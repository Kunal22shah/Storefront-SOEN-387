package org.soen387;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import org.soen387.Product;

public class StorefrontFacade {
    private final Connection connection;

    protected Map<String, Product> productsBySku;
    private final Map<String, Product> productsBySlug;
    private final Map<String, Cart> cartsByUser;

    //Store a list of orders for each user
    private Map<String, ArrayList<Order>> allOrderByUser;

    //Store all Orders made by everyone
    private ArrayList<Order> allOrdersInStore;

    // Constructor
//    public StorefrontFacade() {
//        this.productsBySku = new HashMap<>();
//        this.productsBySlug = new HashMap<>();
//        this.cartsByUser = new HashMap<>();
//    }
    public StorefrontFacade() {

        this.connection = DatabaseConnection.getConnection();
        this.productsBySku = new HashMap<>();
        this.productsBySlug = new HashMap<>();
        this.cartsByUser = new HashMap<>();
        this.allOrderByUser = new HashMap<>();
        this.allOrdersInStore = new ArrayList<>();

    }

    public void createProduct(String sku, String name, String description, String vendor, String urlSlug, double price) {
        if (sku.isEmpty()) {
            throw new RuntimeException("Please add a valid sku");
        } else if (name.isEmpty()) {
            throw new RuntimeException("Please add a valid name");
        }
        if (productsBySku.containsKey(sku)) {
            throw new RuntimeException("Sku is already in use. Please select another sku identifier");
        }
        Product createdProduct = new Product(name, description, vendor, urlSlug, sku, price);
        productsBySku.put(sku, createdProduct);
        productsBySlug.put(urlSlug, createdProduct); // Also add to productsBySlug map

        System.out.println("Product with sku " + productsBySku.get(sku).getSku() + " has been added");
    }

    public void updateProduct(String sku, String name, String description, String vendor, String urlSlug, double price) {
        if (sku.isEmpty()) {
            throw new RuntimeException("Please add a valid sku");
        } else if (name.isEmpty()) {
            throw new RuntimeException("Please add a valid name");
        } else if (description.isEmpty() || vendor.isEmpty() || urlSlug.isEmpty() || Double.isNaN(price)) {
            throw new RuntimeException("Please add all fields correctly");
        }
        if (urlSlug.length() > 100 || !urlSlug.matches("^[0-9a-z-]+$")) {
            throw new RuntimeException("Please add a valid url slug");
        }
        if (!productsBySku.containsKey(sku)) {
            throw new RuntimeException("Product does not exist. Please add product before updating it");
        }
        Product productWithSameSlug = productsBySlug.get(urlSlug);
        if (productWithSameSlug != null && !productWithSameSlug.getSku().equals(sku)) {
            throw new RuntimeException("URL slug is already in use. Please select another slug.");
        }
        Product getUpdatedProduct = productsBySku.get(sku);
        getUpdatedProduct.setName(name);
        getUpdatedProduct.setDescription(description);
        getUpdatedProduct.setVendor(vendor);
        getUpdatedProduct.setUrlSlug(urlSlug);
        getUpdatedProduct.setPrice(price);
        productsBySku.replace(sku, getUpdatedProduct);
        if (!productsBySlug.containsKey(urlSlug)) {
            productsBySlug.put(urlSlug, getUpdatedProduct);
        } else {
            productsBySlug.replace(urlSlug, getUpdatedProduct);
        }
        System.out.println("Product with sku " + productsBySku.get(sku).getSku() + " has been updated");
    }

    public Product getProduct(String sku) {
        // Validate input
        if (sku == null || sku.isEmpty()) {
            throw new IllegalArgumentException("SKU must not be null or empty");
        }

        Product product = null;

        String sql = "SELECT * FROM Products WHERE sku=?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sku);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                product = new Product(
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("vendor"),
                        resultSet.getString("urlSlug"),
                        resultSet.getString("sku"),
                        resultSet.getDouble("price")
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving product with SKU " + sku, e);
        }

        if (product == null) {
            throw new RuntimeException("Product with SKU " + sku + " does not exist");
        }

        return product;
    }


    public ArrayList<Product> getAllProduct() {
        ArrayList<Product> allProducts = new ArrayList<Product>();
        for (Product product : productsBySku.values()) {
            Product oneProduct = new Product(product.getName(), product.getDescription(), product.getVendor(), product.getUrlSlug(), product.getSku(), product.getPrice());
            allProducts.add(oneProduct);
        }
        return allProducts;
    }

    public Product getProductBySlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("Slug must not be null or empty");
        }

        Product product = null;

        String sql = "SELECT * FROM Products WHERE urlSlug=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, slug);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                product = new Product(
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("vendor"),
                        resultSet.getString("urlSlug"),
                        resultSet.getString("sku"),
                        resultSet.getDouble("price")
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving product by slug.", e);
        }

        if (product == null) {
            throw new RuntimeException("Product with slug " + slug + " does not exist");
        }
        return product;
    }

    public Cart getCart(String userEmail) {
        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("User email must not be null or empty");
        }

        Cart cart = new Cart();
        String sql = "SELECT * FROM Carts WHERE userEmail=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userEmail);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Product product = getProduct(resultSet.getString("sku"));
                int quantity = resultSet.getInt("quantity");
                cart.addProduct(product, quantity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving cart.", e);
        }

        return cart;
    }


    public void addProductToCart(String userEmail, String sku) {
        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("User email must not be null or empty");
        }
        if (sku == null || sku.isEmpty()) {
            throw new IllegalArgumentException("SKU must not be null or empty");
        }

        String sqlCheck = "SELECT quantity FROM Carts WHERE userEmail=? AND sku=?";
        try (PreparedStatement checkStmt = connection.prepareStatement(sqlCheck)) {
            checkStmt.setString(1, userEmail);
            checkStmt.setString(2, sku);
            ResultSet resultSet = checkStmt.executeQuery();
            if (resultSet.next()) {
                int quantity = resultSet.getInt("quantity");
                String sqlUpdate = "UPDATE Carts SET quantity=? WHERE userEmail=? AND sku=?";
                try (PreparedStatement updateStmt = connection.prepareStatement(sqlUpdate)) {
                    updateStmt.setInt(1, quantity + 1);
                    updateStmt.setString(2, userEmail);
                    updateStmt.setString(3, sku);
                    updateStmt.executeUpdate();
                }
            } else {
                String sqlInsert = "INSERT INTO Carts(userEmail, sku, quantity) VALUES (?, ?, 1)";
                try (PreparedStatement insertStmt = connection.prepareStatement(sqlInsert)) {
                    insertStmt.setString(1, userEmail);
                    insertStmt.setString(2, sku);
                    insertStmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error adding product to cart.", e);
        }
    }

    public void removeProductFromCart(String userEmail, String sku) {
        if (userEmail == null || userEmail.isEmpty()) {
            throw new IllegalArgumentException("User email must not be null or empty");
        }
        if (sku == null || sku.isEmpty()) {
            throw new IllegalArgumentException("SKU must not be null or empty");
        }

        String sqlCheck = "SELECT quantity FROM Carts WHERE userEmail=? AND sku=?";
        try (PreparedStatement checkStmt = connection.prepareStatement(sqlCheck)) {
            checkStmt.setString(1, userEmail);
            checkStmt.setString(2, sku);
            ResultSet resultSet = checkStmt.executeQuery();
            if (resultSet.next()) {
                int quantity = resultSet.getInt("quantity");
                if (quantity > 1) {
                    String sqlUpdate = "UPDATE Carts SET quantity=? WHERE userEmail=? AND sku=?";
                    try (PreparedStatement updateStmt = connection.prepareStatement(sqlUpdate)) {
                        updateStmt.setInt(1, quantity - 1);
                        updateStmt.setString(2, userEmail);
                        updateStmt.setString(3, sku);
                        updateStmt.executeUpdate();
                    }
                } else {
                    String sqlDelete = "DELETE FROM Carts WHERE userEmail=? AND sku=?";
                    try (PreparedStatement deleteStmt = connection.prepareStatement(sqlDelete)) {
                        deleteStmt.setString(1, userEmail);
                        deleteStmt.setString(2, sku);
                        deleteStmt.executeUpdate();
                    }
                }
            } else {
                return;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error removing product from cart.", e);
        }
    }

    public void downloadProductCatalog() {
        ArrayList<Product> productList = new ArrayList<>();
        String jsonCatalog = "";

        String sqlQuery = "SELECT * FROM Products";

        try (PreparedStatement stmt = connection.prepareStatement(sqlQuery);
             ResultSet resultSet = stmt.executeQuery()) {

            while (resultSet.next()) {
                Product product = new Product(
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("vendor"),
                        resultSet.getString("urlSlug"),
                        resultSet.getString("sku"),
                        resultSet.getDouble("price")
                );
                productList.add(product);
            }

            Gson gson = new Gson();
            jsonCatalog = gson.toJson(productList);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load the product catalog from database", e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("ProductCatalog.json"))) {
            writer.write(jsonCatalog);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to write the product catalog to file", e);
        }
    }

    public ArrayList<Order> getOrders(String user){
        if (user == null || user.isEmpty()){
            throw new IllegalArgumentException("User must not be null or empty");
        }
        String sql = "SELECT * FROM ORDERS WHERE userEmail=?";
        ArrayList<Order> userOrders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                int orderID = resultSet.getInt("orderID");
                String shippingAddress = resultSet.getString("shippingAddress");
                int trackingNumber = resultSet.getInt("trackingNumber");
                boolean isShipped = resultSet.getBoolean("isShipped");
                userOrders.add(new Order(shippingAddress, null, user, orderID, trackingNumber, isShipped));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving Order.", e);
        }
        allOrderByUser.put(user,userOrders);
        return allOrderByUser.get(user);
    }

    public Order getOrder(String user, int id){
        int orderID = id;
        String shippingAddress = "";
        if (user == null){
            String sql = "SELECT orderID, shippingAddress FROM ORDERS WHERE orderID=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    orderID = resultSet.getInt("orderID");
                    shippingAddress = resultSet.getString("shippingAddress");
                }
            } catch (Exception e) {
                throw new RuntimeException("Error retrieving Specific Order.", e);
            }
            ArrayList<Order.OrderProductItem> userOrder = new ArrayList<>();
            sql = "SELECT p.*, op.quantity FROM OrderProduct op JOIN Products p ON op.sku = p.sku WHERE op.orderID = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    Product p = new Product(
                            resultSet.getString("name"),
                            resultSet.getString("description"),
                            resultSet.getString("vendor"),
                            resultSet.getString("urlSlug"),
                            resultSet.getString("sku"),
                            resultSet.getDouble("price")
                    );
                    int quantity = resultSet.getInt("quantity");
                    userOrder.add(new Order.OrderProductItem(p,quantity));
                }
            } catch (Exception e) {
                throw new RuntimeException("Error retrieving Specific Order.", e);
            }
            return new Order(shippingAddress, userOrder, user, orderID, 0, false);
        }
        String sql = "SELECT orderID, shippingAddress FROM ORDERS WHERE orderID=? and userEmail=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2,user);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                orderID = resultSet.getInt("orderID");
                shippingAddress = resultSet.getString("shippingAddress");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving Specific Order.", e);
        }
        ArrayList<Order.OrderProductItem> userOrder = new ArrayList<>();
        sql = "SELECT p.*, op.quantity FROM OrderProduct op JOIN Products p ON op.sku = p.sku WHERE op.orderID = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Product p = new Product(
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("vendor"),
                        resultSet.getString("urlSlug"),
                        resultSet.getString("sku"),
                        resultSet.getDouble("price")
                );
                int quantity = resultSet.getInt("quantity");
                userOrder.add(new Order.OrderProductItem(p,quantity));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving Specific Order.", e);
        }
        return new Order(shippingAddress, userOrder, user, orderID, 0, false);
    }

    public static class ProductAlreadyInCartException extends RuntimeException {
        public ProductAlreadyInCartException(String message) {
            super(message);
        }
    }
}
