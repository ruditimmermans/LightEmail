# Setup help

Setting up LightEmail is fairly simple.  You'll need to add at least one
account to receive email and at least one identity if you want to send email.

## Requirements

An internet connection is required to add accounts and identities.

Your email provider should support secure connections. If your provider doesn't
support secure connections and you care at least a little about your privacy,
you are strongly advised to switch to another provider.

For security reasons, your account need to have a non empty password set.

## Account

To add an account, tap on *Manage accounts* and tap on the orange *add* button
at the bottom.  Select a provider from the list, enter the username, which is
mostly your email address and enter your password. If you use Gmail, tap
*Select account* to fill in the username and password. Tap *Check* to let
LightEmail connect to the email server and fetch a list of system folders.
After reviewing the system folder selection you can add the account by tapping
*Save*.

If your provider is not in the list of providers, select *Custom*.  Enter the
domain name, for example *gmail.com* and tap *Get settings*.  If your provider
supports [auto-discovery](https://tools.ietf.org/html/rfc6186), LightEmail will
fill in the host name and port number, else check the setup instructions of your
provider for the right IMAP host name and port number.

## Identity

Similarly, to add an identity, tap on *Manage identity* and tap on the orange
*add* button at the bottom.  Enter the name you want to appear in de from
address of the emails you send and select a linked account. Tap *Save* to add
the identity.

See [this FAQ](https://framagit.org/dystopia-project/simple-email/blob/1fe638b880e9dfa8346d437e3b0c02036928cf03/docs/FAQ.md#what-are-identities) about using aliases.

## Permissions

If you want to lookup email addresses, have contact photos shown, etc, you'll
need to grant read contacts permission to LightEmail.
Just tap *Grant permissions* and select *Allow*.

## Battery optimizations

On recent Android versions, Android will put apps to sleep when the screen is
off for some time to reduce battery usage.  If you want to receive new emails
without delays, you should disable battery optimizations for LightEmail. Tap
*Disable battery optimizations* and follow the instructions.

## Questions

If you have a question or problem, please [see here][faqs].

 [faqs]: https://framagit.org/dystopia-project/simple-email/blob/8f7296ddc2275471d4190df1dd55dee4025a5114/docs/FAQ.md
