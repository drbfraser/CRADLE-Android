# Script Setup and Usage
The script sets the inAppUpdatePriority for releases, to set it up follow the following steps. 
* Proceed to https://console.cloud.google.com/apis/api/androidpublisher.googleapis.com/credentials?project=cradle-vsa
  and login with icradle2020@gmail.com, the password for which can be found at the Google Drive folder CRADLE Docs >
  Cradle Login Credentials.
* Download the JSON file associated with google-play-publisher-app under the OAuth 2.0 Client IDs section
* rename it client_secrets.json and save it in this directory
  
The script should be run after a draft release is made and saved on the Google Play Console. Once a 
new release is rolled out the inAppUpdatePriority value can no longer be changed. For more in-depth 
information about deployment refer to the documentation in CRADLE docs > Mobile.

