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

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Represents an email provider configuration.
 * This class handles loading provider profiles from an XML resource.
 */
public class Provider {
    /** The name of the provider. */
    public String name;
    /** Documentation URL or text for the provider. */
    public String documentation;
    /** A link associated with the provider. */
    public String link;
    /** The type or ID of the provider (e.g., "gmail", "outlook"). */
    public String type;
    /** The IMAP server host. */
    public String imap_host;
    /** The IMAP server port. */
    public int imap_port;
    /** The POP3 server host. */
    public String pop3_host;
    /** The POP3 server port. */
    public int pop3_port;
    /** The SMTP server host. */
    public String smtp_host;
    /** The SMTP server port. */
    public int smtp_port;
    /** Whether to use STARTTLS for SMTP. */
    public boolean starttls;
    /** The maximum TLS version to use. */
    public String maxtls;

    /**
     * Private constructor for internal use when parsing XML.
     */
    private Provider() {
    }

    /**
     * Constructs a provider with a specific name.
     * @param name The name of the provider.
     */
    Provider(String name) {
        this.name = name;
    }

    /**
     * Loads email provider profiles from the R.xml.providers resource.
     * @param context The application context.
     * @return A list of providers sorted by name.
     */
    static List<Provider> loadProfiles(Context context) {
        List<Provider> result = null;
        try {
            XmlResourceParser xml = context.getResources().getXml(R.xml.providers);
            int eventType = xml.getEventType();
            Provider provider = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if ("providers".equals(xml.getName())) {
                        result = new ArrayList<>();
                    } else if ("provider".equals(xml.getName())) {
                        provider = new Provider();
                        provider.name = xml.getAttributeValue(null, "name");
                        provider.documentation = xml.getAttributeValue(null, "documentation");
                        provider.link = xml.getAttributeValue(null, "link");
                        provider.type = xml.getAttributeValue(null, "type");
                        if (provider.type == null) {
                            provider.type = xml.getAttributeValue(null, "id");
                        }
                        provider.maxtls = xml.getAttributeValue(null, "maxtls");
                    } else if ("imap".equals(xml.getName())) {
                        provider.imap_host = xml.getAttributeValue(null, "host");
                        provider.imap_port = xml.getAttributeIntValue(null, "port", 0);
                    } else if ("pop3".equals(xml.getName())) {
                        provider.pop3_host = xml.getAttributeValue(null, "host");
                        provider.pop3_port = xml.getAttributeIntValue(null, "port", 0);
                    } else if ("smtp".equals(xml.getName())) {
                        provider.smtp_host = xml.getAttributeValue(null, "host");
                        provider.smtp_port = xml.getAttributeIntValue(null, "port", 0);
                        provider.starttls = xml.getAttributeBooleanValue(null, "starttls", false);
                    } else if (!"providers".equals(xml.getName()) &&
                        !"provider".equals(xml.getName()) &&
                        !"imap".equals(xml.getName()) &&
                        !"pop3".equals(xml.getName())) {
                        Log.i(Helper.TAG, "Ignoring tag: " + xml.getName());
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("provider".equals(xml.getName())) {
                        result.add(provider);
                        provider = null;
                    }
                }

                eventType = xml.next();
            }
        } catch (Throwable ex) {
            Log.e(Helper.TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

        Collections.sort(
            result,
            new Comparator<Provider>() {
                @Override
                public int compare(Provider p1, Provider p2) {
                    return collator.compare(p1.name, p2.name);
                }
            });

        return result;
    }

    /**
     * Determines the authentication type based on the provider type.
     * @return One of Helper.AUTH_TYPE_GMAIL, Helper.AUTH_TYPE_OUTLOOK, or Helper.AUTH_TYPE_PASSWORD.
     */
    public int getAuthType() {
        if ("com.google".equals(type) || "gmail".equals(type)) {
            return Helper.AUTH_TYPE_GMAIL;
        }
        if ("com.microsoft".equals(type) ||
            "office365".equals(type) ||
            "office365pcke".equals(type) ||
            "outlookgraph".equals(type) ||
            "outlook".equals(type)) {
            return Helper.AUTH_TYPE_OUTLOOK;
        }
        return Helper.AUTH_TYPE_PASSWORD;
    }

    /**
     * Returns the name of the provider.
     * @return The provider name.
     */
    @Override
    public String toString() {
        return name;
    }
}
