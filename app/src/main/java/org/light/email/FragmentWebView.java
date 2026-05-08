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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

// https://developer.android.com/reference/android/webkit/WebView

public class FragmentWebView extends FragmentEx {
    @Override
    @Nullable
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_webview, container, false);

        final ProgressBar progressBar = view.findViewById(R.id.progressbar);
        final TextView tvFrom = view.findViewById(R.id.tvFrom);
        final WebView webview = view.findViewById(R.id.webview);

        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);

        webview.setBackgroundColor(android.graphics.Color.BLACK);
        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        webview.setWebViewClient(
            new WebViewClient() {
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (prefs.getBoolean("webview", false)) {
                        view.loadUrl(url);
                        setSubtitle(url);
                        return false;
                    } else {
                        Helper.view(getContext(), Uri.parse(url));
                        return true;
                    }
                }
            });

        webview.setWebChromeClient(
            new WebChromeClient() {
                public void onProgressChanged(WebView view, int progress) {
                    progressBar.setProgress(progress);
                    if (progress == 100) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            });

        Bundle args = getArguments();
        if (args.containsKey("url")) {
            String url = args.getString("url");
            webview.loadUrl(url);
            setSubtitle(url);
        } else if (args.containsKey("id")) {
            new SimpleTask<EntityMessage>() {
                @Override
                protected EntityMessage onLoad(Context context, Bundle args) throws Throwable {
                    long id = args.getLong("id");
                    return DB.getInstance(context).message().getMessage(id);
                }

                @Override
                protected void onLoaded(Bundle args, final EntityMessage message) {
                    final String html;
                    try {
                        html = message.read(getContext());
                    } catch (IOException ex) {
                        onException(args, ex);
                        return;
                    }

                    final SharedPreferences prefs =
                        PreferenceManager.getDefaultSharedPreferences(getContext());
                    int bodySp = prefs.getInt("body_text_size_sp", 24);
                    String styledHtml =
                        "<style>body{background-color:black;color:white;font-size:"
                            + bodySp
                            + "px;} ::selection { background: rgba(255, 255, 255, 0.3); }</style>"
                            + html;
                    webview.loadDataWithBaseURL("email://", styledHtml, "text/html", "UTF-8", null);

                    String from =
                        MessageHelper.getFormattedAddresses(
                            message.from, MessageHelper.ADDRESS_FULL);
                    tvFrom.setText(from);
                    tvFrom.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Helper.onAddAddresses(getContext(), message.from);
                            }
                        });
                    setSubtitle(from);
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Helper.unexpectedError(getContext(), ex);
                }
            }.load(this, args);
        }

        ((ActivityBase) getActivity())
            .addBackPressedListener(
                new ActivityBase.IBackPressedListener() {
                    @Override
                    public boolean onBackPressed() {
                        boolean can = webview.canGoBack();
                        if (can) {
                            webview.goBack();
                        }

                        Bundle args = getArguments();
                        if (args.containsKey("from") && !webview.canGoBack()) {
                            setSubtitle(args.getString("from"));
                        }

                        return can;
                    }
                });

        return view;
    }
}
