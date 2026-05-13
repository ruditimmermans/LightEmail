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
import android.text.TextUtils;
import android.util.Log;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class MessageHelper {
    private MimeMessage imessage;
    private String html;
    private List<EntityAttachment> attachments = new ArrayList<>();

    public MessageHelper(MimeMessage imessage) {
        this.imessage = imessage;
        try {
            parse(imessage);
        } catch (Exception ex) {
            Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
        }
    }

    public String getHtml() {
        return html;
    }

    public List<EntityAttachment> getAttachments() {
        return attachments;
    }

    public boolean getSeen() throws MessagingException {
        return imessage.isSet(Flags.Flag.SEEN);
    }

    public boolean getFlagged() throws MessagingException {
        return imessage.isSet(Flags.Flag.FLAGGED);
    }

    public String getMessageID() throws MessagingException {
        return imessage.getMessageID();
    }

    public String[] getReferences() throws MessagingException {
        String header = imessage.getHeader("References", null);
        if (header != null) {
            return header.split("\\s+");
        }
        return new String[0];
    }

    public String getInReplyTo() throws MessagingException {
        return imessage.getHeader("In-Reply-To", null);
    }

    public String getDeliveredTo() throws MessagingException {
        return imessage.getHeader("Delivered-To", null);
    }

    public String getThreadId(long uid) throws MessagingException {
        return getMessageID();
    }

    public Address[] getFrom() throws MessagingException {
        return imessage.getFrom();
    }

    public Address[] getTo() throws MessagingException {
        return imessage.getRecipients(Message.RecipientType.TO);
    }

    public Address[] getCc() throws MessagingException {
        return imessage.getRecipients(Message.RecipientType.CC);
    }

    public Address[] getBcc() throws MessagingException {
        return imessage.getRecipients(Message.RecipientType.BCC);
    }

    public Address[] getReply() throws MessagingException {
        return imessage.getReplyTo();
    }

    public Integer getSize() throws MessagingException {
        return imessage.getSize();
    }

    private void parse(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (html == null && content instanceof String) {
                html = (String) content;
            }
        } else if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (content instanceof String) {
                html = (String) content;
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; mp != null && i < mp.getCount(); i++) {
                parse(mp.getBodyPart(i));
            }
        } else if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part) {
                parse((Part) content);
            }
        } else {
            String name = part.getFileName();
            String contentType = part.getContentType();
            if (name != null || !part.isMimeType("multipart/*")) {
                EntityAttachment attachment = new EntityAttachment();
                attachment.name = name;
                attachment.type = contentType;
                attachment.size = part.getSize();
                if (part instanceof BodyPart) {
                    attachment.part = (BodyPart) part;
                }
                attachments.add(attachment);
            }
        }
    }

    static final String ADDRESS_FULL = "full";
    static final String ADDRESS_NAME = "displayName";
    static final String ADDRESS_COMPOSE = "compose";

    static final int NETWORK_TIMEOUT = 60 * 1000; // milliseconds

    static Properties getSessionProperties(int auth_type, boolean insecure) {
        return getSessionProperties(auth_type, insecure, null);
    }

    static Properties getSessionProperties(int auth_type, boolean insecure, String maxtls) {
        Properties props = new Properties();

        String checkserveridentity = Boolean.toString(!insecure).toLowerCase(Locale.ROOT);

        // https://javaee.github.io/javamail/docs/api/com/sun/mail/imap/package-summary.html#properties
        props.put("mail.imaps.ssl.checkserveridentity", checkserveridentity);
        props.put("mail.imaps.ssl.trust", "*");
        props.put("mail.imaps.starttls.enable", "false");
        props.put("mail.imaps.auth", "true");

        // TODO: make timeouts configurable?
        props.put("mail.imaps.connectiontimeout", Integer.toString(NETWORK_TIMEOUT));
        props.put("mail.imaps.timeout", Integer.toString(NETWORK_TIMEOUT));
        props.put("mail.imaps.writetimeout", Integer.toString(NETWORK_TIMEOUT)); // one thread overhead

        props.put("mail.imaps.connectionpool.debug", "true");
        props.put(
            "mail.imaps.connectionpooltimeout", Integer.toString(3 * 60 * 1000)); // default: 45 sec

        // https://tools.ietf.org/html/rfc4978
        // https://docs.oracle.com/javase/8/docs/api/java/util/zip/Deflater.html
        props.put("mail.imaps.compress.enable", "true");
        // props.put("mail.imaps.compress.level", "-1");
        // props.put("mail.imaps.compress.strategy", "0");

        props.put("mail.imaps.fetchsize", Integer.toString(48 * 1024)); // default 16K
        props.put("mail.imaps.peek", "true");

        props.put("mail.imaps.auth.plain.disable", "false");
        props.put("mail.imaps.auth.login.disable", "false");
        props.put("mail.imaps.auth.ntlm.disable", "true");
        props.put("mail.imaps.auth.gssapi.disable", "true");

        props.put("mail.imap.ssl.checkserveridentity", checkserveridentity);
        props.put("mail.imap.ssl.trust", "*");
        props.put("mail.imap.starttls.enable", "true");
        props.put("mail.imap.starttls.required", "true");
        props.put("mail.imap.auth", "true");

        props.put("mail.imap.connectiontimeout", Integer.toString(NETWORK_TIMEOUT));
        props.put("mail.imap.timeout", Integer.toString(NETWORK_TIMEOUT));
        props.put("mail.imap.writetimeout", Integer.toString(NETWORK_TIMEOUT)); // one thread overhead

        props.put("mail.imap.connectionpool.debug", "true");
        props.put(
            "mail.imap.connectionpooltimeout", Integer.toString(3 * 60 * 1000)); // default: 45 sec

        props.put("mail.imap.compress.enable", "true");

        props.put("mail.imap.fetchsize", Integer.toString(48 * 1024)); // default 16K
        props.put("mail.imap.peek", "true");

        props.put("mail.imap.auth.plain.disable", "false");
        props.put("mail.imap.auth.login.disable", "false");
        props.put("mail.imap.auth.ntlm.disable", "true");
        props.put("mail.imap.auth.gssapi.disable", "true");

        props.put("mail.pop3.ssl.checkserveridentity", checkserveridentity);
        props.put("mail.pop3.ssl.trust", "*");
        props.put("mail.pop3.starttls.enable", "true");
        props.put("mail.pop3.starttls.required", "true");
        props.put("mail.pop3.auth", "true");

        props.put("mail.pop3.auth.plain.disable", "false");
        props.put("mail.pop3.auth.login.disable", "false");
        props.put("mail.pop3.auth.ntlm.disable", "true");
        props.put("mail.pop3.auth.gssapi.disable", "true");

        props.put("mail.pop3s.ssl.checkserveridentity", checkserveridentity);
        props.put("mail.pop3s.ssl.trust", "*");
        props.put("mail.pop3s.auth.plain.disable", "false");
        props.put("mail.pop3s.auth.login.disable", "false");
        props.put("mail.pop3s.auth.ntlm.disable", "true");
        props.put("mail.pop3s.auth.gssapi.disable", "true");

        // https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html#properties
        props.put("mail.smtps.ssl.checkserveridentity", checkserveridentity);
        props.put("mail.smtps.ssl.trust", "*");
        props.put("mail.smtps.starttls.enable", "false");
        props.put("mail.smtps.starttls.required", "false");
        props.put("mail.smtps.auth", "true");

        props.put("mail.smtps.connectiontimeout", Integer.toString(NETWORK_TIMEOUT));
        props.put("mail.smtps.writetimeout", Integer.toString(NETWORK_TIMEOUT)); // one thread overhead
        props.put("mail.smtps.timeout", Integer.toString(NETWORK_TIMEOUT));

        props.put("mail.smtp.ssl.checkserveridentity", checkserveridentity);
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.auth", "true");

        props.put("mail.smtp.connectiontimeout", Integer.toString(NETWORK_TIMEOUT));
        props.put("mail.smtp.writetimeout", Integer.toString(NETWORK_TIMEOUT)); // one thread overhead
        props.put("mail.smtp.timeout", Integer.toString(NETWORK_TIMEOUT));

        props.put("mail.mime.address.strict", "false");
        props.put("mail.mime.decodetext.strict", "false");

        props.put("mail.mime.ignoreunknownencoding", "true"); // Content-Transfer-Encoding
        props.put("mail.mime.decodefilename", "true");
        props.put("mail.mime.encodefilename", "true");

        // https://docs.oracle.com/javaee/6/api/javax/mail/internet/MimeMultipart.html
        props.put(
            "mail.mime.multipart.ignoremissingboundaryparameter",
            "true"); // javax.mail.internet.ParseException: In parameter list
        props.put("mail.mime.multipart.ignoreexistingboundaryparameter", "true");

        // The documentation is unclear/inconsistent whether this are system or session properties:
        System.setProperty("mail.mime.address.strict", "false");
        System.setProperty("mail.mime.decodetext.strict", "false");

        System.setProperty("mail.mime.ignoreunknownencoding", "true"); // Content-Transfer-Encoding
        System.setProperty("mail.mime.decodefilename", "true");
        System.setProperty("mail.mime.encodefilename", "true");

        System.setProperty(
            "mail.mime.multipart.ignoremissingboundaryparameter",
            "true"); // javax.mail.internet.ParseException: In parameter list
        System.setProperty("mail.mime.multipart.ignoreexistingboundaryparameter", "true");

        if (false) {
            Log.i(Helper.TAG, "Prefering IPv4");
            System.setProperty("java.net.preferIPv4Stack", "true");
        }

        if (maxtls != null) {
            String protocols = null;
            if ("1.0".equals(maxtls)) {
                protocols = "TLSv1";
            } else if ("1.1".equals(maxtls)) {
                protocols = "TLSv1 TLSv1.1";
            } else if ("1.2".equals(maxtls)) {
                protocols = "TLSv1 TLSv1.1 TLSv1.2";
            } else if ("1.3".equals(maxtls)) {
                protocols = "TLSv1 TLSv1.1 TLSv1.2 TLSv1.3";
            }

            if (protocols != null) {
                Log.i(Helper.TAG, "Setting TLS protocols=" + protocols);
                props.put("mail.imaps.ssl.protocols", protocols);
                props.put("mail.imap.ssl.protocols", protocols);
                props.put("mail.smtps.ssl.protocols", protocols);
                props.put("mail.smtp.ssl.protocols", protocols);
            }
        }

        // https://javaee.github.io/javamail/OAuth2
        Log.i(Helper.TAG, "Auth type=" + auth_type);
        if (auth_type == Helper.AUTH_TYPE_GMAIL || auth_type == Helper.AUTH_TYPE_OUTLOOK) {
            props.put("mail.imaps.auth.mechanisms", "XOAUTH2 OAUTHBEARER");
            props.put("mail.imap.auth.mechanisms", "XOAUTH2 OAUTHBEARER");
            props.put("mail.pop3s.auth.mechanisms", "XOAUTH2 OAUTHBEARER");
            props.put("mail.pop3.auth.mechanisms", "XOAUTH2 OAUTHBEARER");
            props.put("mail.smtps.auth.mechanisms", "XOAUTH2 OAUTHBEARER");
            props.put("mail.smtp.auth.mechanisms", "XOAUTH2 OAUTHBEARER");
        }

        return props;
    }

    static MimeMessageEx from(
        Context context,
        EntityMessage message,
        EntityMessage reply,
        List<EntityAttachment> attachments,
        Session session)
        throws MessagingException {
        MimeMessageEx mime = new MimeMessageEx(session, null);

        if (reply != null && reply.msgid != null) {
            mime.setHeader("In-Reply-To", reply.msgid);
            if (reply.references == null) {
                mime.setHeader("References", reply.msgid);
            } else {
                mime.setHeader("References", reply.references + " " + reply.msgid);
            }
        }

        if (message.from != null && message.from.length > 0) {
            mime.setFrom(message.from[0]);
        }

        if (message.to != null) {
            mime.setRecipients(Message.RecipientType.TO, message.to);
        }
        if (message.cc != null) {
            mime.setRecipients(Message.RecipientType.CC, message.cc);
        }
        if (message.bcc != null) {
            mime.setRecipients(Message.RecipientType.BCC, message.bcc);
        }

        if (message.reply != null) {
            mime.setReplyTo(message.reply);
        }

        mime.setSubject(message.subject);
        mime.setSentDate(new Date());

        String bodyText = "";
        try {
            bodyText = message.read(context);
        } catch (IOException ex) {
            Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
        }
        if (attachments.size() == 0) {
            mime.setText(bodyText, "utf-8", "html");
        } else {
            Multipart multipart = new MimeMultipart();

            BodyPart body = new MimePartEx();
            body.setContent(bodyText, "text/html; charset=utf-8");
            multipart.addBodyPart(body);

            for (EntityAttachment attachment : attachments) {
                BodyPart part = new MimePartEx();
                // TODO: set content
                multipart.addBodyPart(part);
            }

            mime.setContent(multipart);
        }

        return mime;
    }

    static String getFormattedAddresses(Address[] addresses, String type) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }

        List<String> display = new ArrayList<>();
        for (Address address : addresses) {
            if (address instanceof InternetAddress) {
                InternetAddress internetAddress = (InternetAddress) address;
                String personal = internetAddress.getPersonal();
                String email = internetAddress.getAddress();

                if (ADDRESS_FULL.equals(type)) {
                    display.add((personal == null ? "" : personal + " ") + "<" + email + ">");
                } else if (ADDRESS_NAME.equals(type)) {
                    display.add(personal == null ? email : personal);
                } else if (ADDRESS_COMPOSE.equals(type)) {
                    display.add(
                        (personal == null ? "" : "\"" + personal + "\" ") + "<" + email + ">");
                } else {
                    display.add(email);
                }
            } else {
                display.add(address.toString());
            }
        }
        return TextUtils.join(", ", display);
    }

    static String getFormattedAddresses(String address, String type) {
        try {
            return getFormattedAddresses(InternetAddress.parse(address), type);
        } catch (MessagingException ex) {
            return address;
        }
    }

    static String getAddressesCompose(Address[] addresses) {
        return getFormattedAddresses(addresses, ADDRESS_COMPOSE);
    }

    static void build(Context context, EntityMessage message, List<EntityAttachment> attachments, MimeMessage imessage) {
        MessageHelper helper = new MessageHelper(imessage);
        try {
            message.write(context, helper.getHtml());
            attachments.addAll(helper.getAttachments());
        } catch (IOException ex) {
            Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
        }
    }

    static String sanitizeEmail(String email) {
        return (email == null ? null : email.replaceAll("[\\p{Cntrl}]", ""));
    }

    static class MimePartEx extends MimeBodyPart {
        @Override
        protected void updateHeaders() throws MessagingException {
            super.updateHeaders();
        }
    }
}
