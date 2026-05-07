package org.light.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018, Marcel Bokhorst (M66B)
*/

import androidx.annotation.NonNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlHelper {
    private static final Pattern pattern = Pattern.compile("(https?://[\\S]+)");

    public static String sanitize(String html) {
        Document document =
            Jsoup.parse(Jsoup.clean(html, Safelist.relaxed().addProtocols("img", "src", "cid")));
        for (Element tr : document.select("tr")) {
            tr.after("<br>");
        }
        NodeTraversor.traverse(
            new NodeVisitor() {
                @Override
                public void head(@NonNull Node node, int depth) {
                    if (node instanceof TextNode && !hasAncestor(node, "a")) {
                        String text = ((TextNode) node).getWholeText();
                        Matcher matcher = pattern.matcher(text);
                        StringBuilder sb = new StringBuilder();
                        int last = 0;
                        while (matcher.find()) {
                            sb.append(text, last, matcher.start());
                            String ref = matcher.group();
                            sb.append(String.format("<a href=\"%s\">%s</a>", ref, ref));
                            last = matcher.end();
                        }
                        sb.append(text.substring(last));
                        String linkified = sb.toString();

                        if (linkified.length() > text.length()) {
                            node.before(linkified);
                            node.remove();
                        }
                    }
                }

                @Override
                public void tail(@NonNull Node node, int depth) {
                }

                private boolean hasAncestor(Node node, String tagName) {
                    Node parent = node.parent();
                    while (parent != null) {
                        if (parent instanceof Element && ((Element) parent).tagName().equalsIgnoreCase(tagName)) {
                            return true;
                        }
                        parent = parent.parent();
                    }
                    return false;
                }
            },
            document.body());
        return document.body().html();
    }
}
