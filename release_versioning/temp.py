from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

def authorization():
    flow = InstalledAppFlow.from_client_secrets_file(
        'client_secrets.json',
        scopes=['https://www.googleapis.com/auth/androidpublisher'],
        redirect_uri='urn:ietf:wg:oauth:2.0:oob'
    )
    # this gives a link and need to login through browser with 
    # icradle2020@gmail.com and then paste the token it gives back in the console
    flow.run_console()

    credentials = flow.credentials

    service = build('androidpublisher', 'v3', credentials=credentials)

    app_edit = service.edits().insert(packageName="com.cradleVSA.neptune").execute()

    # you can use this to print out all tracks
    service.edits().tracks().list(packageName="com.cradleVSA.neptune", editId=app_edit["id"]).execute()

    # get a particular track
    internal_track = service.edits().tracks().get(packageName="com.cradleVSA.neptune", editId=app_edit.get("id"), track="internal").execute()

    # there's a releases field; see the googleapis.github.io link 
    # above for info about what's in a release object
    print(internal_track)

    # set the priority for the first release in the list to whatever
    internal_track["releases"][0]["inAppUpdatePriority"] = 5
    print(internal_track)

    # update and commit
    service.edits().tracks().update(packageName="com.cradleVSA.neptune", editId=app_edit["id"], track="internal", body=internal_track).execute()
    service.edits().commit(packageName="com.cradleVSA.neptune", editId=app_edit["id"]).execute()

    # close when you don't need it anymore; you can just copy and paste the above
    # into the console to play around with it without closing the service
    x = '../app/release/app-release.aab'
    service.close()

if __name__ == '__main__':
    authorization()
    