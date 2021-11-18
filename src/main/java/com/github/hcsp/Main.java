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

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {


    public static void main(String[] args) throws IOException, SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:file:/Users/a/zxh-crawler/news");


        //Create a pool to hold links
        List<String> linkpool = new ArrayList<>();
        //从数据库加载即将处理的链接的代码
        try (PreparedStatement statement = connection.prepareStatement("select link from LINKS_TO_BE_PROCESSED")) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                linkpool.add(resultSet.getString(1));
            }
        }

        //从数据库加载已经处理的链接的代码

        Set<String> processedLinks = new HashSet<>();
        //At the beginning, there was only sina's home page
        linkpool.add("http://sina.cn");
        try (PreparedStatement statement = connection.prepareStatement("select link from LINKS_ALREADY_PROCESSED")) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                processedLinks.add(resultSet.getString(1));
            }
        }

        while (true) {
            if (linkpool.isEmpty()) {
                break;
            }
            //Arraylist从尾部删除更有效率
            //处理完后更新数据库

            String link = linkpool.remove(linkpool.size() - 1);

            if (processedLinks.contains(link)) {
                continue;
            }
            if (isInterestingLink(link)) {
                //感兴趣的
                Document doc = httpGetAndParseHtml(link);

                doc.select("a").stream().map(aTag -> aTag.attr("href")).forEach(linkpool::add);

                storeIntoDatabaseIfItIsNewsPage(doc);

                processedLinks.add(link);
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
