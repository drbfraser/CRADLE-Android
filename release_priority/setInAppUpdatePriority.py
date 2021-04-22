from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
import argparse


#Define command line arguments
TRACK_CMD_LINE = 'track'
UPDATE_PRIORITY_CMD_LINE = 'update_priority'


def parseCommandLineArgs():
    parser = argparse.ArgumentParser(description='update inAppUpdatePriority for releases')
    parser.add_argument(f'--{TRACK_CMD_LINE}', help='The track the release is on one of [beta, alpha, internal, production]')
    parser.add_argument(f'--{UPDATE_PRIORITY_CMD_LINE}', help='The update priority for a release, a value from 0-5')
    return vars(parser.parse_args())


def authorizeAndGetService():
    flow = InstalledAppFlow.from_client_secrets_file(
        'client_secrets.json',
        scopes=['https://www.googleapis.com/auth/androidpublisher'],
        redirect_uri='urn:ietf:wg:oauth:2.0:oob'
    )
    # this gives a link and need to login through browser with 
    # icradle2020@gmail.com and then paste the token it gives back in the console
    flow.run_console()

    credentials = flow.credentials
    
    return build('androidpublisher', 'v3', credentials=credentials)


if __name__ == '__main__':
    args = parseCommandLineArgs()
    trackToUpdate = args[TRACK_CMD_LINE]
    updatePriority = args[UPDATE_PRIORITY_CMD_LINE]
    print(trackToUpdate)
    print(updatePriority)

    service = authorizeAndGetService()

    app_edit = service.edits().insert(packageName="com.cradleVSA.neptune").execute()

    # you can use this to print out all tracks
    service.edits().tracks().list(packageName="com.cradleVSA.neptune", editId=app_edit["id"]).execute()

    # get a particular track
    track = service.edits().tracks().get(packageName="com.cradleVSA.neptune", editId=app_edit.get("id"), track=trackToUpdate).execute()

    # there's a releases field; see the googleapis.github.io link 
    # above for info about what's in a release object
    print(track)

    # set the priority for the first release in the list to whatever
    track["releases"][0]["inAppUpdatePriority"] = 5
    print(track)

    # update and commit
    service.edits().tracks().update(packageName="com.cradleVSA.neptune", editId=app_edit["id"], track=trackToUpdate, body=track).execute()
    service.edits().commit(packageName="com.cradleVSA.neptune", editId=app_edit["id"]).execute()

    # close when you don't need it anymore; you can just copy and paste the above
    # into the console to play around with it without closing the service
    service.close()
    