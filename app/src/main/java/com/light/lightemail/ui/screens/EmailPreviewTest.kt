package com.light.lightemail.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.light.lightemail.ui.theme.LightEmailTheme

@Preview(showBackground = true)
@Composable
fun UpsEmailPreview() {
    val upsHtml = """
        <html><body style="background-color: #EBEBE6;color:#60513A;"><table align="center" style="width:600px; background-color: #EBEBE6; font-family: Arial, Helvetica, sans-serif; color:#60513A; font-size:10pt;"><tr><td>
        <table border="0" cellpadding="0" cellspacing="0" width="600" id="headerBlock" style="border-collapse: collapse;background-color: #351d14;">
           <tr>
              <td style="text-align:center;height:3px;font-size: 1px;background-color:#ffb500;line-height:1;">
                 <img src="http://www.ups.com/img/1.gif" alt="" height="3" width="3"></td>
           </tr>
           <tr>
              <td style="padding-top:10px;padding-bottom:5px;">
                 <table border="0" cellpadding="0" cellspacing="0" width="100%">
                    <tr>
                       <td align="left" valign="middle" class="headerLogo" style="text-align:left;padding-left:10px; height:65px;">
                          <img border="0" src="https://www.ups.com/assets/resources/images/UPS_logo_sm.png" width="" height="" alt="UPS logo"></td>
                       <td valign="middle" class="headerHeading" style="padding-left:10px; padding-right:10px; height:65px; min-width:468px;">
                          <h1 style="margin: 0;color: #ffffff;font-size: 30px;font-weight: 100;line-height: 100%;text-align: right;">
                             Receipt
                          </h1>
                       </td>
                    </tr>
                 </table>
              </td>
           </tr>
        </table>
        </td></tr></table></body></html>
    """.trimIndent()

    LightEmailTheme {
        HtmlView(
            html = upsHtml,
            isDark = false,
            textSize = 16f
        )
    }
}
