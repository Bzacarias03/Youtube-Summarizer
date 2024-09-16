from pytube import YouTube # type: ignore

def get_metadata(url: str):
    video_data = []
    try:
        yt = YouTube(url)
        video_data = [
            yt.thumbnail_url,
            yt.title,
            yt.author,
            str(yt.length),
        ]
        return video_data
    except Exception as e:
        return video_data
    