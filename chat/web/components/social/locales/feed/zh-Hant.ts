import type { FeedDict } from '../../feed-text'

const zhHant: FeedDict = {
  // the feed
  'feed.loadFailed': '無法載入貼文',

  // a post
  'post.like': '讚',
  'post.likes.one': '{n} 個讚',
  'post.likes.other': '{n} 個讚',
  'post.comment': '留言',
  'post.comments.one': '{n} 則留言',
  'post.comments.other': '{n} 則留言',
  'post.menu': '更多選項',
  'post.report': '檢舉貼文',
  'post.delete': '刪除貼文',
  'post.deleteTitle': '要刪除這則貼文嗎？',
  'post.deleteBody': '它會連同按讚和留言一起對所有人刪除。',
  'post.deleted': '貼文已刪除',
  'post.public': '所有人',
  'post.friendsOnly': '好友',
  'post.by': '{name} 的貼文',
  'post.photo': '{name} 的照片',
  'post.readMore': '更多',
  'post.gone': '這則貼文不可見',
  'post.goneBody': '它已被刪除，或僅作者的好友可見。',
  'post.reportTitle': '檢舉這則貼文',
  'post.reported': '謝謝，我們會審核這則貼文。',
  'post.viewProfile': '檢視個人頁面',

  // comments
  'comments.title': '留言',
  'comments.none': '還沒有留言',
  'comments.noneBody': '來發表第一則留言吧。',
  'comments.placeholder': '新增留言…',
  'comments.placeholderReply': '回覆 {name}…',
  'comments.label': '你的留言',
  'comments.send': '發佈',
  'comments.reply': '回覆',
  'comments.replyingTo': '正在回覆 {name}',
  'comments.cancelReply': '取消回覆',
  'comments.remove': '刪除',
  'comments.removed': '留言已刪除',
  'comments.report': '檢舉',
  'comments.reportTitle': '檢舉這則留言',
  'comments.reported': '謝謝，我們會審核這則留言。',
  'comments.more': '顯示更多留言',
  'comments.loadFailed': '無法載入留言',

  // the composer
  'compose.audience': '誰可以看',
  'compose.friends': '好友',
  'compose.everyone': '所有人',
  'compose.friendsHint': '你的好友和與你聊過天的人。',
  'compose.everyoneHint': 'yaari 上打開你個人頁面的任何人都能看到。他們也可以加你好友。',
  'compose.checking': '正在檢查照片…',
  'compose.nude': '這張照片疑似裸露內容，無法發佈',

  // profile style
  'style.status': '狀態',
  'style.statusPlaceholder': '今天心情怎麼樣？',
  'style.languages': '我會說的語言',
  'style.languagesHint': '最多選 6 種。',
  'style.languagesMax': '最多只能選擇 6 種語言',
  'style.prompts': '問答',
  'style.promptsHint': '最多回答 3 個。讓別人更容易開口和你聊天。',
  'style.promptAdd': '新增問答',
  'style.promptRemove': '移除這個問答',
  'style.promptQuestion': '問題',
  'style.promptAnswer': '你的回答',
  'style.saved': '資料已更新',
  'style.problem.status': '狀態最多 60 個字元',
  'style.problem.answer': '回答最多 120 個字元',
  'prompt.weekend': '我理想中的週末…',
  'prompt.food': '值得我專程去吃的美食…',
  'prompt.song': '單曲循環中…',
  'prompt.travel': '下一次旅行…',
  'prompt.talk': '我們會合得來，如果…',
  'prompt.laugh': '能讓我笑出來的是…',
  'prompt.goal': '今年我想…',
  'prompt.secret': '很少有人知道的是…',

  // a profile
  'profile.speaks': '會說',
  'style.mbtiHint': "可選。再點一次即可清除。",
}

export default zhHant
