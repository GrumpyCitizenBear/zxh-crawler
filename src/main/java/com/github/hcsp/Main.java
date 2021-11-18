package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.lang.model.element.NestingKind;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static List<String> loadUrlsFromDatabase(Connection connection, String sql) throws SQLException {
        List<String> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        }
        return results;
    }

    public static void main(String[] args) throws IOException, SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:file:/Users/a/zxh-crawler/news", "zxh", "123456");
        while (true) {
            //Create a pool to hold links
            List<String> linkpool = loadUrlsFromDatabase(connection, "SELECT LINK FROM LINKS_TO_BE_PROCESSED");

            //Set<String> processedLinks = new HashSet<>(loadUrlsFromDatabase(connection, "select link from LINKS_ALREADY_PROCESSED"));

            if (linkpool.isEmpty()) {
                break;
            }
            //从待处理池子中捞一个处理
            //处理完后从池子（包括数据库）中删除，
            String link = linkpool.remove(linkpool.size() - 1);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM LINKS_TO_BE_PROCESSED where link = ?")) {
                statement.setString(1, link);
                statement.executeUpdate();
            }
            boolean flag = false;
            try (PreparedStatement statement = connection.prepareStatement("SELECT LINK from LINKS_TO_BE_PROCESSED where link = ?")) {
                statement.setString(1, link);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    flag = true;
                }
            }
            if (flag) {
                continue;
            }
            if (isInterestingLink(link)) {
                //感兴趣的
                Document doc = httpGetAndParseHtml(link);

                for (Element aTag : doc.select("a")) {
                    String href = aTag.attr("href");
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO LINKS_TO_BE_PROCESSED (link)values(?)")) {
                        statement.setString(1, href);
                        statement.executeUpdate();
                    }
                }

                storeIntoDatabaseIfItIsNewsPage(doc);

                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO LINKS_ALREADY_PROCESSED (link)values(?)")) {
                    statement.setString(1, link);
                    statement.executeUpdate();
                }
            }
        }
    }

    private static void storeIntoDatabaseIfItIsNewsPage(Document doc) {
        //假如这是一个新闻的详情页面，那么就存入数据库，否则什么都不做。
        Elements articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                System.out.println(title);
            }
        }
    }

    private static Document httpGetAndParseHtml(String link) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        System.out.println(link);
        if (link.startsWith("//")) {
            link = "https:" + link;
            System.out.println(link);
        }

        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1.2 Safari/605.1.15");
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();
            String html = EntityUtils.toString(entity);
            return Jsoup.parse(html);
        }
    }

    private static boolean isInterestingLink(String link) {
        return (isNewsPage(link) || isIndexPage(link)) &&
                isNotLoginPage(link);
    }

    private static boolean isIndexPage(String link) {
        return "http://sina.cn".equals(link);
    }

    private static boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn");
    }


}
