from youtube_transcript_api import YouTubeTranscriptApi # type: ignore

def caption_extractor(video_id: str) -> str:
    try:
        transcript = YouTubeTranscriptApi.get_transcript(video_id, languages=['en'])
        captions = " ".join([t['text'] for t in transcript])
        return captions
    except Exception as e:
        return "Error"
