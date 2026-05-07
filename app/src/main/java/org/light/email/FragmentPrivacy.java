package org.light.email;

/*
  This file is part of LightEmail.

  LightEmail is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  LightEmail is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with LightEmail.  If not, see <http://www.gnu.org/licenses/>.

  Copyright 2018-2020, Distopico (dystopia project) <distopico@riseup.net> and contributors
*/

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FragmentPrivacy extends FragmentEx {
    @Override
    @Nullable
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.menu_privacy);

        View view = inflater.inflate(R.layout.fragment_privacy, container, false);

        return view;
    }
}
